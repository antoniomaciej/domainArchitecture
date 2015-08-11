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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing.{CommandToTransactionScope, DomainLogic}

import scalaz._

trait TestUserRegistrationModule {

}


/**
 * Projection that should be changes atomically with relation to the handled commands.
 */
trait TheTestState {

  def history(): String

  def countOne(): Int

  def countTwo(): Int

  def countThree(): Int

  def lastAdded(): Int
}

trait TestSideEffects {
  def randomSelectTwoOrThree(): Boolean
}

final class TestUserRegistrationCommandToTransactionScope
  extends CommandToTransactionScope[TheTestCommand, TheTestAggregate, TheTestState] {
  override def calculateTransactionScope(command: TheTestCommand, state: TheTestState):
  CommandToAggregateResult[TheTestAggregate] =
    command match {
      case TestCommandOne() => \/-(Set(TestAggregateOne()))
      case TestCommandTwo(createTwo) => \/-(Set(TestAggregateTwo()))
    }

}

final class TestTestUserRegistrationHandlerLogic(val sideEffects: TestSideEffects) extends
DomainLogic[TheTestCommand, TheTestEvent, TheTestAggregate, TheTestState] {

  override def executeCommand(command: TheTestCommand,
                              transactionScope: Map[TheTestAggregate, Long])
                             (implicit state: TheTestState): CommandToEventsResult[TheTestEvent] =
    command match {
      case TestCommandOne() => \/-(List(TestEventOne()))
      case TestCommandTwo(createTwo) => if (createTwo) {
        \/-(List(TestEventTwo()))
      } else {
        \/-(List(TestEventThree()))
      }
    }

}
