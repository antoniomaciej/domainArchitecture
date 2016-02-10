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

package eu.pmsoft.mcomponents.eventsourcing.projection

import eu.pmsoft.mcomponents.eventsourcing.eventstore.{EventStoreRangeUtils, EventStoreRead}
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import eu.pmsoft.mcomponents.eventsourcing.{EventStoreRange, EventStoreVersion}
import eu.pmsoft.mcomponents.test.Mocked
import org.scalatest.{FlatSpec, Matchers}
import rx.Observable

class AggregateProjectionsTest extends FlatSpec with Matchers {

  val eventStore = new EventStoreRead[TheTestDomainSpecification] {

    val events = List(TestEventOne(), TestEventTwo(), TestEventThree(), TestEventOne())

    override def loadEvents(range: EventStoreRange): Seq[TheTestEvent] = EventStoreRangeUtils.extractRangeFromList(events, range)

    override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Mocked.shouldNotBeCalled

    override def loadEventsForAggregate(aggregate: TheTestAggregate): Seq[TheTestEvent] = aggregate match {
      case TestAggregateOne() => events.filter( _.isInstanceOf[TestEventOne])
      case TestAggregateTwo() => events.filter( _.isInstanceOf[TestEventTwo])
      case TestAggregateThread(nr) => events.filter( _.isInstanceOf[TestEventThread])
    }
  }

  it should "build aggregate as a projection" in {
    //given a event store with events of different aggregates
    //when a projection on an aggregate is build
    val aggregateState = AggregateProjections.buildAggregate(eventStore, new AggregateProjectionForAggregateNrOne())(TestAggregateOne())
    // then only events related to the aggregate are projected
    aggregateState should be(AggregateProjectionTestState(2, false))
  }
  it should "build projection from all events" in {
    //given a event store with events of different aggregates
    //when a projection is build
    val projectionState = AggregateProjections.buildProjection(eventStore, new TestGlobalProjection())
    // then all events are projected
    projectionState should be(TestGlobalProjectionState(2,1,1,0))
  }

}

class TestGlobalProjection extends AggregateProjection[TheTestDomainSpecification, TestGlobalProjectionState] {
  override def zero(): TestGlobalProjectionState = TestGlobalProjectionState()

  override def projectEvent(state: TestGlobalProjectionState, event: TheTestEvent): TestGlobalProjectionState =
  event match {
    case TestEventOne() => state.copy(one = state.one + 1)
    case TestEventTwo() => state.copy(two = state.two + 1)
    case TestEventThree() => state.copy(three = state.three + 1)
    case TestEventThread(threadNr, data) => state.copy(nrOfThreads = state.nrOfThreads + 1)
  }
}

case class TestGlobalProjectionState(one: Int = 0, two: Int = 0, three: Int = 0, nrOfThreads: Int = 0)

class AggregateProjectionForAggregateNrOne extends AggregateProjection[TheTestDomainSpecification, AggregateProjectionTestState] {

  override def zero(): AggregateProjectionTestState = AggregateProjectionTestState()

  override def projectEvent(state: AggregateProjectionTestState, event: TheTestEvent): AggregateProjectionTestState =
    event match {
      case TestEventOne() => state.copy(numberOfEvents = state.numberOfEvents + 1)
      case TestEventTwo() => state.copy(error = true)
      case TestEventThree() => state.copy(error = true)
      case TestEventThread(threadNr, data) => state.copy(error = true)
    }
}

case class AggregateProjectionTestState(numberOfEvents: Int = 0, error: Boolean = false)
