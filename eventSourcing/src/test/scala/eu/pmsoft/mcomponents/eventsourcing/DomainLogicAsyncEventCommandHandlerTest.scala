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
package eu.pmsoft.mcomponents.eventsourcing

import java.util.concurrent.atomic.AtomicInteger

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStore
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import eu.pmsoft.mcomponents.test.{ BaseEventSourceComponentTestSpec, Mocked }
import rx.Observable

import scala.concurrent.{ ExecutionContext, Future }
import scalaz.\/-

class DomainLogicAsyncEventCommandHandlerTest extends BaseEventSourceComponentTestSpec {

  it should "retry to execute commands when result is rollback" in {
    //given a mocked domain logic that returns rollback the first 3 times
    val counter = new AtomicInteger(0)
    val eventStore = new EventStore[RollBackDomain] {

      override def calculateAtomicTransactionScopeVersion(logic: DomainLogic[RollBackDomain], command: RollbackTestCommand): Future[CommandToAtomicState[RollBackDomain]] = Mocked.shouldNotBeCalled

      override def persistEvents(events: List[RollbackTestEvent], aggregateRoot: RollbackTestAggregateId, atomicTransactionScope: AtomicTransactionScope[RollBackDomain]): Future[CommandResult[RollBackDomain]] = {
        if (counter.getAndAdd(1) > 3) {
          Future.successful(scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion.zero, aggregateRoot)))
        }
        else {
          Future.successful(scalaz.-\/(EventSourceCommandRollback()))
        }
      }

      override def loadEvents(range: EventStoreRange): Seq[RollbackTestEvent] = Mocked.shouldNotBeCalled

      override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Mocked.shouldNotBeCalled

      override def loadEventsForAggregate(aggregate: RollbackTestAggregateId): Seq[RollbackTestEvent] = Mocked.shouldNotBeCalled
    }
    val mockedDomainLogic = createMockedDomainLogicForRollback(eventStore)
    //when a command is executed
    val result = mockedDomainLogic.execute(RollbackTestCommand()).futureValue
    //then the command success because of the re-try implementation
    result shouldBe \/-
    counter.get() > 3 shouldBe true
  }

  it should "Propagate errors from event store to the commands" in {
    //given a mocked domain logic that returns rollback the first 3 times
    val eventStore = new EventStore[RollBackDomain] {

      override def calculateAtomicTransactionScopeVersion(logic: DomainLogic[RollBackDomain], command: RollbackTestCommand): Future[CommandToAtomicState[RollBackDomain]] = Mocked.shouldNotBeCalled

      override def persistEvents(events: List[RollbackTestEvent], aggregateRoot: RollbackTestAggregateId, atomicTransactionScope: AtomicTransactionScope[RollBackDomain]): Future[CommandResult[RollBackDomain]] =
        Future.failed(new IllegalStateException("test error"))

      override def loadEvents(range: EventStoreRange): Seq[RollbackTestEvent] = Mocked.shouldNotBeCalled

      override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Mocked.shouldNotBeCalled

      override def loadEventsForAggregate(aggregate: RollbackTestAggregateId): Seq[RollbackTestEvent] = Mocked.shouldNotBeCalled
    }
    val mockedDomainLogic = createMockedDomainLogicForRollback(eventStore)
    //when a command is executed
    val result = mockedDomainLogic.execute(RollbackTestCommand()).failed.futureValue
    //then the command success because of the re-try implementation
    result shouldBe a[IllegalStateException]
  }

  private def createMockedDomainLogicForRollback(eventStore: EventStore[RollBackDomain]) = {

    implicit val eventSourceExecutionContext: EventSourceExecutionContext =
      EventSourceExecutionContextProvider.create()(EventSourcingConfiguration(ExecutionContext.global, LocalBindingInfrastructure.create(), Set()))

    val fullLogic = new EventStore[RollBackDomain] with VersionedEventStoreView[RollBackDomain#State] {

      override def calculateAtomicTransactionScopeVersion(logic: DomainLogic[RollBackDomain], command: RollbackTestCommand): Future[CommandToAtomicState[RollBackDomain]] = {
        Future.successful(scalaz.\/-(AtomicTransactionScope[RollBackDomain](Map(), Map(), RollbackTestState())))
      }

      override def persistEvents(
        events:                 List[RollbackTestEvent],
        aggregateRoot:          RollbackTestAggregateId,
        atomicTransactionScope: AtomicTransactionScope[RollBackDomain]
      ): Future[CommandResult[RollBackDomain]] =
        eventStore.persistEvents(events, aggregateRoot, atomicTransactionScope)

      override def lastSnapshot(): RollbackTestState = RollbackTestState()

      override def loadEvents(range: EventStoreRange): Seq[RollbackTestEvent] = Mocked.shouldNotBeCalled

      override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Mocked.shouldNotBeCalled

      override def atLeastOn(storeVersion: EventStoreVersion): Future[VersionedProjection[RollbackTestState]] = Mocked.shouldNotBeCalled

      override def loadEventsForAggregate(aggregate: RollbackTestAggregateId): Seq[RollbackTestEvent] = Mocked.shouldNotBeCalled
    }

    new DomainLogicAsyncEventCommandHandler[RollBackDomain](
      new RollBackDomainLogic(),
      new RollbackTestSideEffect {},
      fullLogic,
      EventSourcingConfiguration(ExecutionContext.Implicits.global, LocalBindingInfrastructure.create(), Set())
    )
  }

}

final class RollBackDomain extends DomainSpecification {
  type Command = RollbackTestCommand
  type Event = RollbackTestEvent
  type Aggregate = RollbackTestAggregateId
  type ConstraintScope = RollbackConstraintScope
  type State = RollbackTestState
  type SideEffects = RollbackTestSideEffect
}

case class RollbackTestCommand()

case class RollbackTestEvent()

case class RollbackTestAggregateId()

case class RollbackTestState()

case class RollbackConstraintScope()

trait RollbackTestSideEffect {}

class RollBackDomainLogic extends DomainLogic[RollBackDomain] {

  override def executeCommand(command: RollbackTestCommand, atomicTransactionScope: AtomicTransactionScope[RollBackDomain])(implicit state: RollbackTestState, sideEffects: RollbackTestSideEffect): CommandToEventsResult[RollBackDomain] =
    scalaz.\/-(CommandModelResult(List(RollbackTestEvent()), RollbackTestAggregateId()))

  override def calculateRootAggregate(command: RollbackTestCommand, state: RollbackTestState): CommandToAggregateScope[RollBackDomain] =
    scalaz.\/-(Set(RollbackTestAggregateId()))

}
