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

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.{ CommandResult, CommandToAtomicState }
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.atomic.{ LazyLoadByVersion, Atomic }
import eu.pmsoft.mcomponents.eventsourcing.eventstore.inmemory.EventStoreInMemoryAtomicProjection
import eu.pmsoft.mcomponents.eventsourcing.eventstore.sql._
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import rx.Observable
import rx.subjects.BehaviorSubject

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scalaz.{ \/-, -\/ }

object EventStoreProvider {

  def createEventStore[D <: DomainSpecification, P <: D#State](
    stateCreationLogic:  EventStoreAtomicProjectionCreationLogic[D, P],
    schema:              EventSerializationSchema[D],
    eventStoreReference: EventStoreReference[D]
  )(implicit eventSourcingConfiguration: EventSourcingConfiguration): EventStore[D] with VersionedEventStoreView[D#Aggregate, P] = {
    val backendStrategy = eventSourcingConfiguration.backendStrategies.find(_.eventStoreReference == eventStoreReference)
    val backend = backendStrategy match {
      case None => throw new IllegalStateException(s"backend for event store ${eventStoreReference} not configured")
      case Some(strategy) =>
        strategy match {
          case EventStoreInMemory(eventStoreReference)             => new EventStoreInMemoryAtomicProjection(stateCreationLogic, schema)
          case backendConfig @ EventStoreSqlBackend(_, _, _, _, _) => new EventStoreSqlAtomicProjection(stateCreationLogic, schema, backendConfig.asInstanceOf[EventStoreSqlBackend[D]])
        }
    }
    //TODO life cycle
    backend.initializeBackend()
    val eventStore = new AtomicEventStoreWithProjection(eventStoreReference, backend)(eventSourcingConfiguration)
    eventSourcingConfiguration.bindingInfrastructure.producerApi.registerEventStore(eventStoreReference, eventStore)
    eventStore
  }

}

private final class AtomicEventStoreWithProjection[D <: DomainSpecification, P <: D#State](
  val eventStoreReference: EventStoreReference[D],
  val eventStoreBackend:   EventStoreTransactionalBackend[D, P]
)(implicit val eventSourcingConfiguration: EventSourcingConfiguration) extends EventStore[D]
    with VersionedEventStoreView[D#Aggregate, P]
    with LazyLoadByVersion[P] {

  private[this] val eventCreationSubject: BehaviorSubject[EventStoreVersion] = BehaviorSubject.create(EventStoreVersion.zero)

  override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = eventCreationSubject

  override def lastSnapshot(): P = eventStoreBackend readOnly { transaction =>
    transaction.projectionState
  }

  override def getCurrentVersion(): VersionedProjection[P] = eventStoreBackend readOnly { transaction =>
    VersionedProjection[P](transaction.eventStoreVersion, transaction.projectionState)
  }

  override def calculateAtomicTransactionScopeVersion(logic: DomainLogic[D], command: D#Command): Future[CommandToAtomicState[D]] = eventStoreBackend readOnly { transaction =>
    val projectionState: P = transaction.projectionState
    val result = logic.calculateTransactionScope(command, projectionState).map { setAggregated =>
      val aggregatesVersions: Map[D#Aggregate, Long] = transaction.calculateAggregatesVersions(setAggregated)
      AtomicTransactionScope[D](aggregatesVersions, projectionState)
    }
    Future.successful(result)
  }

  override def loadEvents(range: EventStoreRange): Seq[D#Event] = eventStoreBackend readOnly { transaction =>
    transaction.extractEventRange(range)
  }

  override def persistEvents(events: List[D#Event], aggregateRoot: D#Aggregate, atomicTransactionScope: AtomicTransactionScope[D]): Future[CommandResult] = {
    val result = eventStoreBackend.persistEventsOnAtomicTransaction(events, aggregateRoot, atomicTransactionScope.transactionScopeVersion)
    result match {
      case -\/(a) =>
      case \/-(success) => {
        eventCreationSubject.onNext(success.storeVersion)
        triggerNewVersionAvailable(success.storeVersion)
      }
    }
    Future.successful(result)
  }

  override def loadEventsForAggregate(aggregate: D#Aggregate): Seq[D#Event] = eventStoreBackend readOnly { transaction =>
    transaction.loadEventsForAggregate(aggregate)
  }
}

