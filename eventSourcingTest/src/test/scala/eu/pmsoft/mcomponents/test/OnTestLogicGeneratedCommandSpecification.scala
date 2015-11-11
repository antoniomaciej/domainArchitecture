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

package eu.pmsoft.mcomponents.test

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.{CommandResultConfirmed, CommandToAggregateResult, CommandToEventsResult}
import eu.pmsoft.mcomponents.eventsourcing._
import org.scalacheck.Gen

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/-


class TestLogicDomainSpecification extends DomainSpecification {
  type Command = TheCommand
  type Event = TheEvent
  type Aggregate = TheAggregate
  type State = TheState
}

class OnTestLogicGeneratedCommandSpecification extends BaseEventSourceSpec
with GeneratedCommandSpecification[TestLogicDomainSpecification,
  TheApplicationContract] {

  override def bindingInfrastructure: BindingInfrastructure = Mocked.shouldNotBeCalled

  override implicit def eventSourceExecutionContext: EventSourceExecutionContext = Mocked.shouldNotBeCalled

  override def buildGenerator(state: AtomicEventStoreView[TheState]): CommandGenerator[TheCommand] = new TheCommandGenerator()

  override def createEmptyModule(): TheApplicationContract = new TheApplicationContract()

  override def postCommandValidation(state: TheState, command: TheCommand): Unit = state.ok

  override def validateState(state: TheState): Unit = state.ok
}

sealed trait TheCommand

case class CommandOne() extends TheCommand

case class CommandTwo() extends TheCommand

sealed trait TheEvent

case class EventOne() extends TheEvent

case class EventTwo() extends TheEvent

sealed trait TheAggregate

case class AggregateOne() extends TheAggregate

case class AggregateTwo() extends TheAggregate

trait TheState {
  def ok: Boolean
}

case class SuccessState() extends TheState {
  override def ok: Boolean = true
}

case class FailureState() extends TheState {
  override def ok: Boolean = false
}

class TheApplicationContract extends AbstractApplicationContract[TestLogicDomainSpecification] {
  override implicit def eventSourceExecutionContext: EventSourceExecutionContext = Mocked.shouldNotBeCalled

  override def commandHandler: AsyncEventCommandHandler[TestLogicDomainSpecification] =
    new AsyncEventCommandHandler[TestLogicDomainSpecification] {
      override def execute(command: TheCommand): Future[CommandResultConfirmed] =
        Future.successful(\/-(EventSourceCommandConfirmation(EventStoreVersion(0L))))
    }

  override def atomicProjection: VersionedEventStoreView[TheAggregate, TheState] =
    new VersionedEventStoreView[TheAggregate, TheState] {
      override def projection(transactionScope: Set[TheAggregate]): Future[VersionedProjection[TheAggregate, TheState]] =
        Future.successful(VersionedProjection[TheAggregate, TheState](Map(AggregateOne() -> 0L), SuccessState()))

      override def lastSnapshot(): Future[TheState] = Future.successful(SuccessState())

      override def atLeastOn(storeVersion: EventStoreVersion): Future[TheState] = Future.successful(SuccessState())
    }

  override implicit def executionContext: ExecutionContext = Mocked.shouldNotBeCalled

  override def storeStorage: AsyncEventStore[TheEvent, TheAggregate] = Mocked.shouldNotBeCalled

  override def transactionScopeCalculator: CommandToTransactionScope[TestLogicDomainSpecification] =
    new CommandToTransactionScope[TestLogicDomainSpecification] {
      override def calculateTransactionScope(command: TheCommand, state: TheState): CommandToAggregateResult[TheAggregate] =
        \/-(Set[TheAggregate]())
    }

  override def logic: DomainLogic[TestLogicDomainSpecification] =
    new DomainLogic[TestLogicDomainSpecification] {
      override def executeCommand(command: TheCommand, transactionScope: Map[TheAggregate, Long])
                                 (implicit state: TheState): CommandToEventsResult[TheEvent] = command match {
        case CommandOne() => \/-(List(EventOne()))
        case CommandTwo() => \/-(List(EventTwo()))
      }
    }
}

class TheCommandGenerator extends CommandGenerator[TheCommand] {
  override def generateSingleCommands: Gen[TheCommand] = genOneOrTwo

  override def generateWarmUpCommands: Gen[List[TheCommand]] = Gen.nonEmptyListOf(genOneOrTwo)

  lazy val genOneOrTwo = Gen.oneOf(genOne, genTwo)

  lazy val genOne = Gen.const(CommandOne())
  lazy val genTwo = Gen.const(CommandTwo())
}
