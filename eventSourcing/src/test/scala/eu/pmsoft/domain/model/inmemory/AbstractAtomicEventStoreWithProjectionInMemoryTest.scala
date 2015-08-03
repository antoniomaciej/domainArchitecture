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

package eu.pmsoft.domain.model.inmemory

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, Executor, Executors, TimeUnit}

import eu.pmsoft.mcomponents.eventsourcing.inmemory.AbstractAtomicEventStoreWithProjectionInMemory
import eu.pmsoft.mcomponents.eventsourcing.{EventSourceCommandFailed, EventSourceCommandRollback, EventStoreVersion}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import org.typelevel.scalatest.DisjunctionMatchers

import scala.language.postfixOps
import scalaz.{-\/, \/-}

class AbstractAtomicEventStoreWithProjectionInMemoryTest extends FlatSpec with Matchers with PropertyChecks with ScalaFutures with DisjunctionMatchers {

  it should "provide projections on the future" in {

    //given a event store that is a OrderedEventStoreProjector
    val testEventStoreInMemory = new NoOpEventStoreInMemory()
    //and a version number to extract
    val versionNumber = 4L
    val versionToExtract = EventStoreVersion(versionNumber)
    //and two projections extracted with atLeastOn
    val futureStateOne = testEventStoreInMemory.atLeastOn(versionToExtract)
    val futureStateTwo = testEventStoreInMemory.atLeastOn(versionToExtract)

    //when events are added
    def addOneEvent(eventNr: Int): Unit = {
      val transactionScope = testEventStoreInMemory.projection(Set(TestAggregate(1))).futureValue
      testEventStoreInMemory.persistEvents(List(TestEvent(eventNr)), transactionScope.transactionScopeVersion).futureValue shouldBe \/-
    }
    addOneEvent(0)
    addOneEvent(1)
    addOneEvent(2)

    //then the projections are not retrieved before the event store version is match
    futureStateOne.isCompleted shouldBe false
    futureStateTwo.isCompleted shouldBe false
    //and they complete when the version is match
    addOneEvent(3)
    futureStateOne.futureValue.events shouldBe List(TestEvent(3), TestEvent(2), TestEvent(1), TestEvent(0))
    //and future events do not change the projections retrieved
    addOneEvent(4)
    futureStateTwo.futureValue.events shouldBe List(TestEvent(3), TestEvent(2), TestEvent(1), TestEvent(0))
    //but if in the future the atLeastOn operations is used with a version number that is in the pass
    val afterAll = testEventStoreInMemory.atLeastOn(versionToExtract)
    //then the last version is returned
    afterAll.futureValue.events shouldBe List(TestEvent(4), TestEvent(3), TestEvent(2), TestEvent(1), TestEvent(0))
  }

  it should "detect concurrent execution of updates in the same transaction scope" in {
    //given
    val testEventStoreInMemory = new NoOpEventStoreInMemory()
    val nrOfThreads = 4
    val barriers = new CountDownLatch(nrOfThreads)
    val rollbackError = new AtomicBoolean(false)

    //when 4 thread are executed in parallel to store on the same transaction context Map(TestAggregate(1) -> 0L)
    val service: Executor = Executors.newFixedThreadPool(nrOfThreads)
    (1 to 4) foreach { thread =>
      service.execute(new Runnable {
        override def run(): Unit = {
          while (!rollbackError.get()) {
            val transactionScope = testEventStoreInMemory.projection(Set(TestAggregate(1))).futureValue
            testEventStoreInMemory.persistEvents(List(TestEvent(0)), transactionScope.transactionScopeVersion).futureValue match {
              case -\/(error) => error match {
                case EventSourceCommandRollback() => rollbackError.set(true)
                case EventSourceCommandFailed(_) =>
              }
              case \/-(b) =>
            }
          }
          barriers.countDown()
        }
      })
    }

    barriers.await(1, TimeUnit.SECONDS)
    //then a error related to concurrency transaction scopes must be found
    rollbackError.get() shouldBe true
  }

  it should "run concurrently in different transaction scope" in {
    //given
    val testEventStoreInMemory = new NoOpEventStoreInMemory()
    val nrOfThreads = 4
    val barriers = new CountDownLatch(nrOfThreads)
    val rollbackError = new AtomicBoolean(false)

    //when 4 thread are executed in parallel to store on the same execution context
    val service: Executor = Executors.newFixedThreadPool(nrOfThreads)
    (1 to 4) foreach { thread =>
      service.execute(new Runnable {
        override def run(): Unit = {
          while (!rollbackError.get()) {
            val transactionScope = testEventStoreInMemory.projection(Set(TestAggregate(thread))).futureValue
            testEventStoreInMemory.persistEvents(List(TestEvent(0)), transactionScope.transactionScopeVersion) futureValue match {
              case -\/(error) => error match {
                case EventSourceCommandRollback() => rollbackError.set(true)
                case EventSourceCommandFailed(_) =>
              }
              case \/-(b) =>
            }
          }
          barriers.countDown()
        }
      })
    }

    barriers.await(1, TimeUnit.SECONDS)
    //then a error related to concurrency transaction scopes must be found
    rollbackError.get() shouldBe false
  }


  class NoOpEventStoreInMemory extends AbstractAtomicEventStoreWithProjectionInMemory[TestEvent, TestAggregate, TestState] {
    override def buildInitialState(): TestState = TestState()

    override def projectSingleEvent(state: TestState, event: TestEvent): TestState = {
      state.copy(events = event :: state.events)
    }
  }

  case class TestAggregate(scope: Int)

  case class TestEvent(anyData: Int)

  case class TestState(events: List[TestEvent] = List())

}
