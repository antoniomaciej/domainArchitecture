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

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreRead
import org.scalacheck.Gen

import scalaz.\/-

class TestLogicDomainSpecification extends DomainSpecification {
  type Command = TheCommand
  type Event = TheEvent
  type Aggregate = TheAggregate
  type ConstraintScope = TheConstraintScope
  type State = TheState
  type SideEffects = TheSideEffect
}

class TestDomainLogic extends DomainLogic[TestLogicDomainSpecification] {

  override def executeCommand(
    command:                TheCommand,
    atomicTransactionScope: AtomicTransactionScope[TestLogicDomainSpecification]
  )(implicit projectionView: TheState, sideEffects: TheSideEffect): CommandToEventsResult[TestLogicDomainSpecification] =
    command match {
      case CommandOne() => \/-(CommandModelResult(List(EventOne()), AggregateOne()))
      case CommandTwo() => \/-(CommandModelResult(List(EventTwo()), AggregateTwo()))
    }

  override def calculateAggregates(command: TheCommand, state: TheState): CommandToAggregateScope[TestLogicDomainSpecification] =
    command match {
      case CommandOne() => \/-(Set(AggregateOne()))
      case CommandTwo() => \/-(Set(AggregateTwo()))
    }

  override def calculateConstraints(command: TheCommand, state: TheState): CommandToConstraints[TestLogicDomainSpecification] =
    command match {
      case CommandOne()  => \/-(Set(ConstraintScopeOne()))
      case CommandTwo() => \/-(Set(ConstraintScopeTwo()))
    }
}

sealed trait TheCommand

case class CommandOne() extends TheCommand

case class CommandTwo() extends TheCommand

sealed trait TheEvent

case class EventOne() extends TheEvent

case class EventTwo() extends TheEvent

case class EventWithData(nr: Long) extends TheEvent

sealed trait TheAggregate

case class AggregateOne() extends TheAggregate

case class AggregateTwo() extends TheAggregate

sealed trait TheConstraintScope

case class ConstraintScopeOne() extends TheConstraintScope

case class ConstraintScopeTwo() extends TheConstraintScope


trait TheState {
  def ok: Boolean
}

trait TheSideEffect {

}

case class SuccessState() extends TheState {
  override def ok: Boolean = true
}

case class FailureState() extends TheState {
  override def ok: Boolean = false
}

class TheCommandGenerator(implicit eventStoreRead: EventStoreRead[TestLogicDomainSpecification]) extends CommandGenerator[TheCommand] {
  override def generateSingleCommands: Gen[TheCommand] = genOneOrTwo

  override def generateWarmUpCommands: Gen[List[TheCommand]] = Gen.nonEmptyListOf(genOneOrTwo)

  lazy val genOneOrTwo = Gen.oneOf(genOne, genTwo)

  lazy val genOne = Gen.const(CommandOne())
  lazy val genTwo = Gen.const(CommandTwo())
}
