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
 *
 */

package eu.pmsoft.mcomponents.eventsourcing.inmemory

import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel.CommandResult
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}
import scalaz.{-\/, \/, \/-}


abstract class AbstractAtomicEventStoreWithProjectionInMemory[E, A, S]
  extends AsyncEventStore[E, A] with VersionedEventStoreProjectionView[A, S] {

  protected[this] val inMemoryStore = Atomic(AtomicEventStoreState[E, A, S](buildInitialState()))

  def buildInitialState(): S

  def projectSingleEvent(state: S, event: E): S


  override def persistEvents(events: List[E], transactionScopeVersion: Map[A, Long]): Future[CommandResult] = {
    def checkIfStateMatchTransactionScopeVersion(state: AtomicEventStoreState[E, A, S]):
    EventSourceCommandFailure \/ AtomicEventStoreState[E, A, S] = transactionScopeVersion.find({
      case (aggregate, version) => state.aggregatesVersion.getOrElse(aggregate, 0L).compareTo(version) != 0
    }) match {
      case Some(notMatchingAggregateVersion) => -\/(EventSourceCommandRollback())
      case None => \/-(state)
    }

    def updateState(state: AtomicEventStoreState[E, A, S]): AtomicEventStoreState[E, A, S] = {
      val updatedEventHistory = state.eventHistory ++ events
      val updatedStateProjection = (state.state /: events)(projectSingleEvent)
      val updatedAggregatesVersion = state.aggregatesVersion ++ (transactionScopeVersion mapValues (_ + 1))
      AtomicEventStoreState(updatedStateProjection, updatedEventHistory, updatedAggregatesVersion)
    }
    val afterUpdate = inMemoryStore.updateAndGetWithCondition(updateState, checkIfStateMatchTransactionScopeVersion)
    afterUpdate.map(triggerDelayedProjections)
    Future.successful(
      afterUpdate.map { state =>
        EventSourceCommandConfirmation(EventStoreVersion(state.eventHistory.length))
      }
    )
  }

  override def lastSnapshot(): Future[S] = Future.successful(inMemoryStore().state)

  private val futureProjections = TrieMap[EventStoreVersion, Promise[S]]()

  private def triggerDelayedProjections(state: AtomicEventStoreState[E, A, S]) = {
    futureProjections.filterKeys {
      _.storeVersion <= state.eventHistory.size
    } foreach { pair =>
      pair._2.trySuccess(state.state)
    }
    val toRemove = futureProjections.filter(_._2.isCompleted)
    toRemove.foreach {
      case (key, promise) => futureProjections.remove(key, promise)
    }
  }

  private def waitAsyncForFutureProjection(storeVersion: EventStoreVersion): Future[S] = {
    val promise = Promise[S]()
    val futureProjection = futureProjections.putIfAbsent(storeVersion, promise) match {
      case Some(oldPromise) => oldPromise.future
      case None => promise.future
    }
    // it may be the case, that between the check on atLeastOn and insertion of the promise the value of the state changed
    // to a correct storeVersion, so trigger a additional check here
    triggerDelayedProjections(inMemoryStore())
    futureProjection
  }

  override def atLeastOn(storeVersion: EventStoreVersion): Future[S] = {
    val currState = inMemoryStore()
    if (currState.eventHistory.size >= storeVersion.storeVersion) {
      Future.successful(currState.state)
    } else {
      waitAsyncForFutureProjection(storeVersion)
    }
  }

  override def projection(transactionScope: Set[A]): Future[VersionedProjection[A, S]] = {
    val atomicState = inMemoryStore()
    val transactionScopeVersion: Map[A, Long] = transactionScope.map { aggregate =>
      aggregate -> atomicState.aggregatesVersion.getOrElse(aggregate, 0L)
    }(collection.breakOut)
    Future.successful(VersionedProjection[A, S](transactionScopeVersion, atomicState.state))
  }
}

case class AtomicEventStoreState[E, A, S](state: S,
                                          eventHistory: List[E] = List[E](),
                                          aggregatesVersion: Map[A, Long] = Map[A, Long]())

