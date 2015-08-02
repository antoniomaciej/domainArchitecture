package eu.pmsoft.mcomponents.eventsourcing

import java.util.concurrent.atomic.AtomicInteger

import eu.pmsoft.domain.model.{Mocked, ComponentSpec}
import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel._

import scala.concurrent.{ExecutionContext, Future}

class DomainLogicAsyncEventCommandHandlerTest  extends ComponentSpec {

  it should "retry to execute commands when result is rollback" in {
    //given a mocked domain logic that returns rollback the first 3 times
    val eventStore = new AsyncEventStore[RollbackTestEvent, RollbackTestAggregateId] {
      val counter = new AtomicInteger(0)

      override def persistEvents(events: List[RollbackTestEvent], transactionScopeVersion: Map[RollbackTestAggregateId, Long]): Future[CommandResult] = {
        if (counter.getAndAdd(1) > 3) {
          Future.successful(scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion(0L))))
        } else {
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
    val eventStore = new AsyncEventStore[RollbackTestEvent, RollbackTestAggregateId] {
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

  private def createMockedDomainLogicForRollback(eventStore: AsyncEventStore[RollbackTestEvent, RollbackTestAggregateId]) =
    new DomainLogicAsyncEventCommandHandler[RollbackTestCommand, RollbackTestEvent, RollbackTestAggregateId, RollbackTestState] {
      override protected def logic: DomainLogic[RollbackTestCommand, RollbackTestEvent, RollbackTestAggregateId, RollbackTestState] =
        new DomainLogic[RollbackTestCommand, RollbackTestEvent, RollbackTestAggregateId, RollbackTestState] {
          override def executeCommand(command: RollbackTestCommand, transactionScope: Map[RollbackTestAggregateId, Long])
                                     (implicit state: RollbackTestState): CommandToEventsResult[RollbackTestEvent] =
            scalaz.\/-(List(RollbackTestEvent()))
        }

      override protected def atomicProjection: VersionedEventStoreProjection[RollbackTestAggregateId, RollbackTestState] =
        new VersionedEventStoreProjection[RollbackTestAggregateId, RollbackTestState] {
          override def projection(transactionScope: Set[RollbackTestAggregateId]):
          Future[VersionedProjection[RollbackTestAggregateId, RollbackTestState]] =
            Future.successful(VersionedProjection(Map(), RollbackTestState()))

          override def lastSnapshot(): Future[RollbackTestState] = Future.successful(RollbackTestState())

          override def atLeastOn(storeVersion: EventStoreVersion): Future[RollbackTestState] = Mocked.shouldNotBeCalled
        }

      override implicit def executionContext: ExecutionContext = ExecutionContext.global

      override protected lazy val store: AsyncEventStore[RollbackTestEvent, RollbackTestAggregateId] = eventStore


      override protected def transactionScopeCalculator: CommandToTransactionScope[RollbackTestCommand, RollbackTestAggregateId, RollbackTestState] =
        new CommandToTransactionScope[RollbackTestCommand, RollbackTestAggregateId, RollbackTestState] {
          override def calculateTransactionScope(command: RollbackTestCommand, state: RollbackTestState): CommandToAggregateResult[RollbackTestAggregateId] =
            scalaz.\/-(Set(RollbackTestAggregateId()))
        }
    }


}

case class RollbackTestCommand()

case class RollbackTestEvent()

case class RollbackTestAggregateId()

case class RollbackTestState()
