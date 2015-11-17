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
import eu.pmsoft.mcomponents.eventsourcing.eventstore.AsyncEventStore
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.test.{ BaseEventSourceComponentTestSpec, Mocked }

import scala.concurrent.{ ExecutionContext, Future }

class DomainLogicAsyncEventCommandHandlerTest extends BaseEventSourceComponentTestSpec {

  it should "retry to execute commands when result is rollback" in {
    //given a mocked domain logic that returns rollback the first 3 times
    val eventStore = new AsyncEventStore[RollBackDomain] {
      val counter = new AtomicInteger(0)

      override def persistEvents(events: List[RollbackTestEvent], transactionScopeVersion: Map[RollbackTestAggregateId, Long]): Future[CommandResult] = {
        if (counter.getAndAdd(1) > 3) {
          Future.successful(scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion(0L))))
        }
        else {
          Future.successful(scalaz.-\/(EventSourceCommandRollback()))
        }
      }
    }
    val mockedDomainLogic = createMockedDomainLogicForRollback(eventStore)
    //when a command is executed
    val result = mockedDomainLogic.execute(RollbackTestCommand()).futureValue
    //then the command success because of the re-try implementation
    result shouldBe \/-
  }

  it should "Propagate errors from event store to the commands" in {
    //given a mocked domain logic that returns rollback the first 3 times
    val eventStore = new AsyncEventStore[RollBackDomain] {
      val counter = new AtomicInteger(0)

      override def persistEvents(events: List[RollbackTestEvent], transactionScopeVersion: Map[RollbackTestAggregateId, Long]): Future[CommandResult] =
        Future.failed(new IllegalStateException("test error"))
    }
    val mockedDomainLogic = createMockedDomainLogicForRollback(eventStore)
    //when a command is executed
    val result = mockedDomainLogic.execute(RollbackTestCommand()).failed.futureValue
    //then the command success because of the re-try implementation
    result shouldBe a[IllegalStateException]
  }

  private def createMockedDomainLogicForRollback(eventStore: AsyncEventStore[RollBackDomain]) = {

    implicit val eventSourceExecutionContext: EventSourceExecutionContext =
      EventSourceExecutionContextProvider.create()(EventSourcingConfiguration(ExecutionContext.global, LocalBindingInfrastructure.create()))

    val fullLogic = new AsyncEventStore[RollBackDomain] with VersionedEventStoreView[RollBackDomain#Aggregate, RollBackDomain#State] {
      override def persistEvents(events: List[RollbackTestEvent], transactionScopeVersion: Map[RollbackTestAggregateId, Long]): Future[CommandResult] =
        eventStore.persistEvents(events, transactionScopeVersion)

      override def projection(transactionScope: Set[RollbackTestAggregateId]): Future[VersionedProjection[RollbackTestAggregateId, RollbackTestState]] =
        Future.successful(VersionedProjection(Map(), RollbackTestState()))

      override def lastSnapshot(): Future[RollbackTestState] = Future.successful(RollbackTestState())

      override def atLeastOn(storeVersion: EventStoreVersion): Future[RollbackTestState] = Mocked.shouldNotBeCalled

    }

    new DomainLogicAsyncEventCommandHandler[RollBackDomain](
      new RollBackDomainLogic(),
      new RollbackTestSideEffect {},
      fullLogic
    )
  }

}

final class RollBackDomain extends DomainSpecification {
  type Command = RollbackTestCommand
  type Event = RollbackTestEvent
  type Aggregate = RollbackTestAggregateId
  type State = RollbackTestState
  type SideEffects = RollbackTestSideEffect
}

case class RollbackTestCommand()

case class RollbackTestEvent()

case class RollbackTestAggregateId()

case class RollbackTestState()

trait RollbackTestSideEffect {}

class RollBackDomainLogic extends DomainLogic[RollBackDomain] {
  override def executeCommand(command: RollbackTestCommand, transactionScope: Map[RollbackTestAggregateId, Long])(implicit state: RollbackTestState, sideEffect: RollbackTestSideEffect): CommandToEventsResult[RollbackTestEvent] =
    scalaz.\/-(List(RollbackTestEvent()))

  override def calculateTransactionScope(command: RollbackTestCommand, state: RollbackTestState): CommandToAggregateResult[RollbackTestAggregateId] =
    scalaz.\/-(Set(RollbackTestAggregateId()))

}
