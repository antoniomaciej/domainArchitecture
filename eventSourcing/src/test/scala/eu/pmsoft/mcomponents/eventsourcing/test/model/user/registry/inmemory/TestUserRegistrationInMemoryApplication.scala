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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry.inmemory

import java.util.concurrent.atomic.AtomicLong

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.inmemory.EventStoreWithProjectionInMemoryLogic
import eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry._

import scala.reflect.{ClassTag, classTag}


object TestUserRegistrationInMemoryApplication {

  def infrastructure()(implicit eventSourceExecutionContext: EventSourceExecutionContext): TheTestInfrastructure =
    new TheTestInfrastructure {

      private lazy val storeAndProjectionInMemory = eventSourceExecutionContext.createInMemoryEventStore(
        new TestUserRegistrationInMemoryLogic(),
        new TestUserRegistrationIdentifier(EventStoreID("test"))
      )

      override lazy val sideEffects: TestSideEffects = new TestLocalThreadSideEffects()

      override lazy val atomicProjection: VersionedEventStoreView[TheTestAggregate,
        TheTestState] = storeAndProjectionInMemory

      override lazy val storeStorage: AsyncEventStore[TheTestEvent,
        TheTestAggregate] = storeAndProjectionInMemory
    }
}

class TestLocalThreadSideEffects extends TestSideEffects {
  val uidCounter = new AtomicLong(0)

  override def randomSelectTwoOrThree(): Boolean = uidCounter.addAndGet(1) % 2 == 0
}

class TestUserRegistrationInMemoryLogic
  extends EventStoreWithProjectionInMemoryLogic[TheTestEvent,
    TheTestAggregate,
    TestStateInMemory] {
  override def buildInitialState(): TestStateInMemory = TestStateInMemory()

  override def projectSingleEvent(state: TestStateInMemory, event: TheTestEvent): TestStateInMemory = event match {
    case TestEventOne() => state.copy(one = state.one + 1, lastAdded = 1, history = "1" + state.history)
    case TestEventTwo() => state.copy(two = state.two + 1, lastAdded = 2, history = "2" + state.history)
    case TestEventThree() => state.copy(three = state.three + 1, lastAdded = 3, history = "3" + state.history)
  }
}

class TestUserRegistrationIdentifier(val id: EventStoreID) extends EventStoreIdentification[TheTestEvent, TheTestAggregate] {
  override def rootEventType: ClassTag[TheTestEvent] = classTag[TheTestEvent]

  override def aggregateRootType: ClassTag[TheTestAggregate] = classTag[TheTestAggregate]

}


case class TestStateInMemory(one: Int = 0,
                             two: Int = 0,
                             three: Int = 0,
                             lastAdded: Int = 0,
                             history: String = "") extends TheTestState {
  override def countOne(): Int = one

  override def countTwo(): Int = two

  override def countThree(): Int = three

}
