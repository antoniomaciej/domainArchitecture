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

package eu.pmsoft.mcomponents.eventsourcing.test.model.test

import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreRead
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import eu.pmsoft.mcomponents.test.{ BaseEventSourceSpec, CommandGenerator, GeneratedCommandSpecification }

import scalaz.\/-

class TestUserRegistrationModuleTest extends BaseEventSourceSpec with GeneratedCommandSpecification[TheTestDomainSpecification] {

  override def backendStrategy: EventStoreBackendStrategy[TheTestDomainSpecification] = EventStoreInMemory(TheTestDomainModule.eventStoreReference)

  override def bindingInfrastructure: BindingInfrastructure = LocalBindingInfrastructure.create()

  implicit def eventSourceExecutionContext: EventSourceExecutionContext = EventSourceExecutionContextProvider.create()

  override def implementationModule(): DomainModule[TheTestDomainSpecification] = new TheTestDomainModule()

  override def buildGenerator(state: AtomicEventStoreView[TheTestState])(implicit eventStoreRead: EventStoreRead[TheTestDomainSpecification]): CommandGenerator[TheTestCommand] = new TestUserRegistrationGenerators(state)

  override def postCommandValidation(state: TheTestState, command: TheTestCommand,
                                     result: EventSourceCommandConfirmation[TheTestAggregate])(implicit eventStoreRead: EventStoreRead[TheTestDomainSpecification]): Unit = command match {
    case TestCommandForThreads(threadNr, targetAggregate) => ()
    case TestCommandOne()                                 => state.lastAdded() should be(1)
    case TestCommandTwo(createTwo) => if (createTwo) {
      state.lastAdded() should be(2)
    }
    else {
      state.lastAdded() should be(3)
    }
  }

  override def validateState(state: TheTestState)(implicit eventStoreRead: EventStoreRead[TheTestDomainSpecification]): Unit = {
      def countState(expected: Char)(counter: Int, char: Char) = {
        if (char == expected) {
          counter + 1
        }
        else {
          counter
        }
      }
    state.history().foldLeft(0)(countState('1')) should be(state.countOne())
    state.history().foldLeft(0)(countState('2')) should be(state.countTwo())
    state.history().foldLeft(0)(countState('3')) should be(state.countThree())
  }
}
