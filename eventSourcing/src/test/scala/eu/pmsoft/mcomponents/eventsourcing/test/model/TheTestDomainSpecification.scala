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

package eu.pmsoft.mcomponents.eventsourcing.test.model

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._

import scalaz._

final class TheTestDomainSpecification extends DomainSpecification {
  type Command = TheTestCommand
  type Event = TheTestEvent
  type Aggregate = TheTestAggregate
  type ConstraintScope = TheTestConstraintScope
  type State = TheTestState
  type SideEffects = TestSideEffects
}

/** Projection that should be changes atomically with relation to the handled commands.
 */
trait TheTestState {

  def history(): String

  def countOne(): Int

  def countTwo(): Int

  def countThree(): Int

  def lastAdded(): Int

  def events: List[TheTestEvent]
}

trait TestSideEffects {
  def randomSelectTwoOrThree(): Boolean
}

final class TheTestEventSerializationSchema extends EventSerializationSchema[TheTestDomainSpecification] {
  override def mapToEvent(data: EventDataWithNr): TheTestEvent = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    data.eventBytes.unpickle[TheTestEvent]
  }

  override def buildConstraintReference(constraintScope: TheTestConstraintScope): ConstraintReference =
    constraintScope match {
      case TestConstraintOne() => ConstraintReference(0,"one")
      case TestConstraintTwo(constraintNr) => ConstraintReference(0,constraintNr)
    }

  override def buildAggregateReference(aggregate: TheTestAggregate): AggregateReference = aggregate match {
    case TestAggregateOne()      => AggregateReference(0, 0)
    case TestAggregateTwo()      => AggregateReference(1, 0)
    case TestAggregateThread(nr) => AggregateReference(2, nr)
  }

  override def eventToData(event: TheTestEvent): EventData = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    EventData(event.pickle.value)
  }

}

final class TheTestDomainLogic extends DomainLogic[TheTestDomainSpecification] {

  override def calculateRootAggregate(command: TheTestCommand, state: TheTestState): CommandToAggregateScope[TheTestDomainSpecification] =
    command match {
      case TestCommandOne()                                 => \/-(Set(TestAggregateOne()))
      case TestCommandTwo(createTwo)                        => \/-(Set(TestAggregateTwo()))
      case TestCommandForThreads(threadNr, targetAggregate) => \/-(Set(TestAggregateThread(targetAggregate)))
    }

  override def executeCommand(
    command:                TheTestCommand,
    atomicTransactionScope: AtomicTransactionScope[TheTestDomainSpecification]
  )(implicit state: TheTestState, sideEffects: TestSideEffects): CommandToEventsResult[TheTestDomainSpecification] =
    command match {
      case TestCommandForThreads(threadNr, targetAggregate) =>
        \/-(CommandModelResult(List(TestEventThread(threadNr, targetAggregate)), TestAggregateThread(threadNr)))
      case TestCommandOne() =>
        \/-(CommandModelResult(List(TestEventOne()), TestAggregateOne()))
      case TestCommandTwo(createTwo) => if (createTwo) {
        \/-(CommandModelResult(List(TestEventTwo()), TestAggregateTwo()))
      }
      else {
        \/-(CommandModelResult(List(TestEventThree()), TestAggregateTwo()))
      }
    }

}
