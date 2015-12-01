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

package eu.pmsoft.mcomponents.eventsourcing.eventstore.inmemory

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreRangeUtils, EventStoreAtomicProjectionCreationLogic, EventStoreReadTransaction, EventStoreTransactionalBackend }

import scalaz.{ -\/, \/, \/- }

class EventStoreInMemoryAtomicProjection[D <: DomainSpecification, P <: D#State](
  val stateCreationLogic: EventStoreAtomicProjectionCreationLogic[D, P],
  val schema:             EventSerializationSchema[D]
)
    extends EventStoreTransactionalBackend[D, P] {

  private[this] val inMemoryStore = Atomic(AtomicEventStoreReadStateInMemory[D, P](stateCreationLogic.buildInitialState()))

  override def initializeBackend(): Unit = ()

  override def readOnly[A](execution: (EventStoreReadTransaction[D, P]) => A): A = execution(new InMemoryAtomicEventStoreReadTransaction(inMemoryStore(), schema))

  override def persistEventsOnAtomicTransaction(events: List[D#Event], rootAggregate: D#Aggregate, transactionScopeVersion: Map[D#Aggregate, Long]): CommandResult = {
    val eventsOfAggregate = events.map(EventOfAggregate(_, rootAggregate))
    val aggregatesReferenceVersion = transactionScopeVersion.map {
      case (aggregate, version) => (schema.buildReference(aggregate), version)
    }
      def checkIfStateMatchTransactionScopeVersion(state: AtomicEventStoreReadStateInMemory[D, P]): EventSourceCommandFailure \/ AtomicEventStoreReadStateInMemory[D, P] = {
        aggregatesReferenceVersion.find({
          case (aggregateRef, aggregateVersion) => state.aggregatesVersion.getOrElse(aggregateRef, 0L).compareTo(aggregateVersion) != 0
        }) match {
          case Some(notMatchingAggregateVersion) => -\/(EventSourceCommandRollback())
          case None                              => \/-(state)
        }
      }

      def updateState(state: AtomicEventStoreReadStateInMemory[D, P]): AtomicEventStoreReadStateInMemory[D, P] = {
        val updatedEventHistory = state.eventHistoryList ++ eventsOfAggregate
        val updatedStateProjection = (state.state /: events)(stateCreationLogic.projectSingleEvent)
        val updatedAggregatesVersion = state.aggregatesVersion ++ (aggregatesReferenceVersion mapValues (_ + 1))
        val updatedVersion = state.version.add(events.size)
        AtomicEventStoreReadStateInMemory(updatedStateProjection, updatedEventHistory, updatedVersion, updatedAggregatesVersion)
      }
    inMemoryStore.updateAndGetWithCondition(updateState, checkIfStateMatchTransactionScopeVersion).map { state =>
      EventSourceCommandConfirmation(EventStoreVersion(state.eventHistoryList.length))
    }
  }
}

private class InMemoryAtomicEventStoreReadTransaction[D <: DomainSpecification, P <: D#State](
    val inMemoryState: AtomicEventStoreReadStateInMemory[D, P],
    val schema:        EventSerializationSchema[D]
) extends EventStoreReadTransaction[D, P] {
  override def calculateAggregatesVersions(aggregates: Set[D#Aggregate]): Map[D#Aggregate, Long] =
    aggregates.map { aggregate =>
      aggregate -> inMemoryState.aggregatesVersion.getOrElse(schema.buildReference(aggregate), 0L)
    }(collection.breakOut)

  override def eventStoreVersion: EventStoreVersion = inMemoryState.version

  override def projectionState: P = inMemoryState.state

  override def extractEventRange(range: EventStoreRange): Stream[D#Event] = {
    EventStoreRangeUtils.extractRangeFromList(inMemoryState.eventHistoryList, range).map(_.event).toStream
  }

  override def loadEventsForAggregate(aggregate: D#Aggregate): Seq[D#Event] =
    inMemoryState.eventHistoryList.filter(_.aggregate == aggregate).map(_.event)
}

private case class AtomicEventStoreReadStateInMemory[D <: DomainSpecification, P <: D#State](
  state:             P,
  eventHistoryList:  List[EventOfAggregate[D]]     = List[EventOfAggregate[D]](),
  version:           EventStoreVersion             = EventStoreVersion.zero,
  aggregatesVersion: Map[AggregateReference, Long] = Map[AggregateReference, Long]()
)

private case class EventOfAggregate[D <: DomainSpecification](event: D#Event, aggregate: D#Aggregate)
