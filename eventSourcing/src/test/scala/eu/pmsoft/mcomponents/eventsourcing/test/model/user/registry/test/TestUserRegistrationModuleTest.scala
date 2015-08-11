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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry.test

import eu.pmsoft.mcomponents.eventsourcing.AtomicEventStoreView
import eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry._
import eu.pmsoft.mcomponents.test.{BaseEventSourceSpec, CommandGenerator, GeneratedCommandSpecification}

abstract class TestUserRegistrationModuleTest extends BaseEventSourceSpec with
GeneratedCommandSpecification[TheTestCommand, TheTestEvent,
  TheTestState, TheTestAggregate, TestUserRegistrationApplication] {

  def infrastructure(): TheTestInfrastructure

  override def buildGenerator(state: AtomicEventStoreView[TheTestState]): CommandGenerator[TheTestCommand] =
    new TestUserRegistrationGenerators(state)

  override def createEmptyModule(): TestUserRegistrationApplication = new TestUserRegistrationApplication(infrastructure())

  override def postCommandValidation(state: TheTestState, command: TheTestCommand): Unit = command match {
    case TestCommandOne() => state.lastAdded() should be(1)
    case TestCommandTwo(createTwo) => if (createTwo) {
      state.lastAdded() should be(2)
    } else {
      state.lastAdded() should be(3)
    }
  }

  override def validateState(state: TheTestState): Unit = {
    def countState(expected: Char)(counter: Int, char: Char) = {
      if (char == expected) {
        counter + 1
      } else {
        counter
      }
    }
    state.history().foldLeft(0)(countState('1')) should be(state.countOne())
    state.history().foldLeft(0)(countState('2')) should be(state.countTwo())
    state.history().foldLeft(0)(countState('3')) should be(state.countThree())
  }
}
