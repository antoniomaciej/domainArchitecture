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
 */

package eu.pmsoft.domain.inmemory

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.{AppendedClues, FlatSpec, Matchers}

import scala.concurrent.Future

class AbstractAtomicEventStoreWithProjectionInMemoryTest extends FlatSpec with Matchers with PropertyChecks with ScalaFutures with AppendedClues {

  it should "detect concurrent execution of updates" in {

    //given a multi thread execution context
    import scala.concurrent.ExecutionContext.Implicits.global
    //and a
    val testEventStoreInMemory = new NoOpEventStoreInMemory()

    //when 50 updates in parallel are executed
    val allParallel = Future.traverse(0 to 50) { projectionNr =>
      //The internal implementation execute in the local thread,
      // so a future executed in the executionContext is necessary
      // to make the execution really parallel
      Future {
        projectionNr
      } map { nr =>
        testEventStoreInMemory.persistEvents(List(TestEvent(nr)))
      }
    }

    //then a exception is throw
    allParallel.failed.futureValue shouldBe a[IllegalStateException]

  }

  class NoOpEventStoreInMemory extends AbstractAtomicEventStoreWithProjectionInMemory[TestEvent, TestState] {
    override def buildInitialState(): TestState = TestState()

    override def projectSingleEvent(state: TestState, event: TestEvent): TestState = {
      //delay is introduced to make the race condition more probable
      val projectionEventDelay = 10
      Thread.sleep(projectionEventDelay)
      state.copy(events = event :: state.events)
    }
  }

  case class TestEvent(anyData: Int)

  case class TestState(events: List[TestEvent] = List())

}
