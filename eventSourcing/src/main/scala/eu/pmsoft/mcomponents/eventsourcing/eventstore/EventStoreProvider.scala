/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Paweł Cesar Sanjuan Szklarz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package eu.pmsoft.mcomponents.eventsourcing.eventstore

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.CommandResult
import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic
import eu.pmsoft.mcomponents.eventsourcing.{ DomainSpecification, VersionedEventStoreView, _ }

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ Future, Promise }
import scalaz._

object EventStoreProvider {

  def createEventStore[D <: DomainSpecification, P <: D#State](
    logic:              EventStoreAtomicProjection[D#Event, P],
    identificationInfo: EventStoreIdentification[D]
  ): EventStore[D] with VersionedEventStoreView[D#Aggregate, P] = {
    new AtomicEventStoreWithProjection(logic, identificationInfo)
  }

}

final class AtomicEventStoreWithProjection[D <: DomainSpecification, P <: D#State](
  val logic:              EventStoreAtomicProjection[D#Event, P],
  val identificationInfo: EventStoreIdentification[D]
)

    extends EventStore[D] with VersionedEventStoreView[D#Aggregate, P] {

  protected[this] val inMemoryStore = Atomic(AtomicEventStoreState[D, P](logic.buildInitialState()))

  override def persistEvents(events: List[D#Event], transactionScopeVersion: Map[D#Aggregate, Long]): Future[CommandResult] = {
      def checkIfStateMatchTransactionScopeVersion(state: AtomicEventStoreState[D, P]): EventSourceCommandFailure \/ AtomicEventStoreState[D, P] = transactionScopeVersion.find({
        case (aggregate, aggregateVersion) => state.aggregatesVersion.getOrElse(aggregate, 0L).compareTo(aggregateVersion) != 0
      }) match {
        case Some(notMatchingAggregateVersion) => -\/(EventSourceCommandRollback())
        case None                              => \/-(state)
      }

      def updateState(state: AtomicEventStoreState[D, P]): AtomicEventStoreState[D, P] = {
        val updatedEventHistory = state.eventHistory ++ events
        val updatedStateProjection = (state.state /: events)(logic.projectSingleEvent)
        val updatedAggregatesVersion = state.aggregatesVersion ++ (transactionScopeVersion mapValues (_ + 1))
        val updatedVersion = state.version.add(events.size)
        AtomicEventStoreState(updatedStateProjection, updatedEventHistory, updatedVersion, updatedAggregatesVersion)
      }
    val afterUpdate = inMemoryStore.updateAndGetWithCondition(updateState, checkIfStateMatchTransactionScopeVersion)
    afterUpdate.map(triggerDelayedProjections)
    Future.successful(
      afterUpdate.map { state =>
        EventSourceCommandConfirmation(EventStoreVersion(state.eventHistory.length))
      }
    )
  }

  override def lastSnapshot(): Future[P] = Future.successful(inMemoryStore().state)

  private val futureProjections = TrieMap[EventStoreVersion, Promise[P]]()
  private val futureLoads = TrieMap[EventStoreRange, Promise[Seq[D#Event]]]()

  private def triggerDelayedProjections(state: AtomicEventStoreState[D, P]) = {
    futureProjections.filterKeys {
      _.storeVersion <= state.version.storeVersion
    } foreach { pair =>
      pair._2.trySuccess(state.state)
    }
    val toRemove = futureProjections.filter(_._2.isCompleted)
    toRemove.foreach {
      case (key, promise) => futureProjections.remove(key, promise)
    }

    futureLoads.filterKeys {
      _.from.storeVersion <= state.version.storeVersion
    } foreach { pair =>
      pair._2.trySuccess(extractEventRange(pair._1, state))
    }
    futureLoads.filter(_._2.isCompleted).foreach {
      case (key, promise) => futureLoads.remove(key, promise)
    }
  }

  private def extractEventRange(range: EventStoreRange, state: AtomicEventStoreState[D, P]): Seq[D#Event] = {
      def dropFrom(events: List[D#Event]) = {
        val toDrop = range.from.storeVersion.toInt
        if (toDrop > 1) {
          events.drop(toDrop - 1)
        }
        else {
          events
        }
      }
      def takeLimit(events: List[D#Event]) = range.to match {
        case Some(limit) => events.take((limit.storeVersion - range.from.storeVersion).toInt).toSeq
        case None        => events.toSeq
      }
    takeLimit(dropFrom(state.eventHistory))
  }

  override def loadEvents(range: EventStoreRange): Future[Seq[D#Event]] = {
    val currentState = inMemoryStore()
    if (currentState.version.storeVersion >= range.from.storeVersion) {
      Future.successful(extractEventRange(range, currentState))
    }
    else {
      val promise = Promise[Seq[D#Event]]()
      val futureProjection = futureLoads.putIfAbsent(range, promise) match {
        case Some(oldPromise) => oldPromise.future
        case None             => promise.future
      }
      triggerDelayedProjections(inMemoryStore())
      futureProjection
    }
  }

  private def waitAsyncForFutureProjection(storeVersion: EventStoreVersion): Future[P] = {
    val promise = Promise[P]()
    val futureProjection = futureProjections.putIfAbsent(storeVersion, promise) match {
      case Some(oldPromise) => oldPromise.future
      case None             => promise.future
    }
    // it may be the case, that between the check on atLeastOn and insertion of the promise the value of the state changed
    // to a correct storeVersion, so trigger a additional check here
    triggerDelayedProjections(inMemoryStore())
    futureProjection
  }

  override def atLeastOn(storeVersion: EventStoreVersion): Future[P] = {
    val currState = inMemoryStore()
    if (currState.version.storeVersion >= storeVersion.storeVersion) {
      Future.successful(currState.state)
    }
    else {
      waitAsyncForFutureProjection(storeVersion)
    }
  }

  override def projection(transactionScope: Set[D#Aggregate]): Future[VersionedProjection[D#Aggregate, P]] = {
    val atomicState = inMemoryStore()
    val transactionScopeVersion: Map[D#Aggregate, Long] = transactionScope.map { aggregate =>
      aggregate -> atomicState.aggregatesVersion.getOrElse(aggregate, 0L)
    }(collection.breakOut)
    Future.successful(VersionedProjection[D#Aggregate, P](transactionScopeVersion, atomicState.state))
  }
}

//TODO extract event store state to a implementation part
case class AtomicEventStoreState[D <: DomainSpecification, P <: D#State](
  state:             P,
  eventHistory:      List[D#Event]          = List[D#Event](),
  version:           EventStoreVersion      = EventStoreVersion(0),
  aggregatesVersion: Map[D#Aggregate, Long] = Map[D#Aggregate, Long]()
)
