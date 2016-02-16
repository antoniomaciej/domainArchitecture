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

package eu.pmsoft.mcomponents.eventsourcing.eventstore

import java.util.concurrent._
import java.util.concurrent.atomic.{ AtomicReference, AtomicBoolean }

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Tag, FlatSpec, Matchers }
import org.typelevel.scalatest.DisjunctionMatchers

import scalaz._

trait EventStoreWithVersionedEventStoreViewBehaviour {
  self: FlatSpec with Matchers with PropertyChecks with ScalaFutures with DisjunctionMatchers =>

  def addOneEvent(eventStore: EventStore[TheTestDomainSpecification] with VersionedEventStoreView[TheTestState])(eventNr: Int): Unit = {
    addOneEventOnThreadRoot(eventStore)(0, eventNr)
  }

  def addOneEventOnThreadRoot(eventStore: EventStore[TheTestDomainSpecification] with VersionedEventStoreView[TheTestState])(threadNr: Int, eventNr: Int): Unit = {
    val atomicTransactionScope = eventStore.calculateAtomicTransactionScopeVersion(new TheTestDomainLogic(), TestCommandForThreads(0, 0)).futureValue.toOption.get
    eventStore.persistEvents(List(TestEventThread(threadNr, eventNr)), TestAggregateThread(threadNr), atomicTransactionScope).futureValue
  }

  def eventStoreWithAtomicProjection(testsTag: Tag, eventStoreCreator: () => EventStore[TheTestDomainSpecification] with VersionedEventStoreView[TheTestState]): Unit = {

    it should "provide projections on the future" taggedAs testsTag in {
      //given
      val eventStore = eventStoreCreator()
      //and a version number to extract
      val versionNumber = 4L
      val versionToExtract = EventStoreVersion(versionNumber)
      //and two projections extracted with atLeastOn
      val futureStateOne = eventStore.atLeastOn(versionToExtract)
      val futureStateTwo = eventStore.atLeastOn(versionToExtract)

      //when events are added
      val addFunc: (Int) => Unit = addOneEvent(eventStore)
      addFunc(1)
      addFunc(2)
      addFunc(3)

      //then the projections are not retrieved before the event store version is match
      futureStateOne.isCompleted shouldBe false
      futureStateTwo.isCompleted shouldBe false
      //and they complete when the version is match
      addFunc(4)
      futureStateOne.futureValue.projection.events shouldBe List(TestEventThread(0, 1), TestEventThread(0, 2), TestEventThread(0, 3), TestEventThread(0, 4))
      //and future events do not change the projections retrieved
      addFunc(5)
      futureStateTwo.futureValue.projection.events shouldBe List(TestEventThread(0, 1), TestEventThread(0, 2), TestEventThread(0, 3), TestEventThread(0, 4))
      //but if in the future the atLeastOn operations is used with a version number that is in the pass
      val afterAll = eventStore.atLeastOn(versionToExtract)
      //then the last version is returned
      afterAll.futureValue.projection.events shouldBe List(TestEventThread(0, 1), TestEventThread(0, 2), TestEventThread(0, 3), TestEventThread(0, 4), TestEventThread(0, 5))
      //and this match the range [1,inf)
      eventStore.loadEvents(EventStoreRange(EventStoreVersion.zero, None)) shouldBe List(TestEventThread(0, 1), TestEventThread(0, 2), TestEventThread(0, 3), TestEventThread(0, 4), TestEventThread(0, 5))
    }

    it should "range [2,4) and [3,inf) is correctly extracted" taggedAs testsTag in {
      //given
      val eventStore = eventStoreCreator()
      //and a number of events to generate
      val nrOfEvents = 10
      val rangeClose: EventStoreRange = EventStoreRange(EventStoreVersion(2L), Some(EventStoreVersion(4L)))
      val rangeOpen: EventStoreRange = EventStoreRange(EventStoreVersion(3L), None)
      //and a to load ranges is run before the events creation
      val load1To4BeforeEventsClose = eventStore.loadEvents(rangeClose)
      val load1To4BeforeEventsOpen = eventStore.loadEvents(rangeOpen)

      //when events are added
      val addFunc: (Int) => Unit = addOneEvent(eventStore)
      (1 to nrOfEvents).foreach(addFunc)
      //then the preLoad call is empty
      load1To4BeforeEventsClose shouldBe empty
      load1To4BeforeEventsOpen shouldBe empty
      //and a load after the events provide all the available events
      val futureLoad1To4AfterEventsClose = eventStore.loadEvents(rangeClose)
      val futureLoad1To4AfterEventsOpen = eventStore.loadEvents(rangeOpen)
      futureLoad1To4AfterEventsClose shouldBe List(TestEventThread(0, 2), TestEventThread(0, 3), TestEventThread(0, 4))
      futureLoad1To4AfterEventsOpen shouldBe (3 to 10).map(TestEventThread(0, _))
    }

    it should "extract events for a root aggregate" in {
      //given
      val eventStore = eventStoreCreator()
      //and a number of events to generate
      val nrOfEvents = 10

      //when events are added on thread root aggregates
      val addFunc: (Int, Int) => Unit = addOneEventOnThreadRoot(eventStore)
      for {
        thread <- 0 to 4
        eventNr <- 1 to nrOfEvents
      } addFunc(thread, eventNr)
        //then each aggregate have it own events

        def eventsOnAggregate(aggNr: Int): Seq[TheTestEvent] = (1 to nrOfEvents).map(TestEventThread(aggNr, _))

      for {
        threadNr <- 0 to 4
      } eventStore.loadEventsForAggregate(TestAggregateThread(threadNr)) should be(eventsOnAggregate(threadNr))

    }

    it should "detect concurrent execution of updates in the same transaction scope" taggedAs testsTag in {
      val calculationTime: Int = 3000
      runConcurrentEventPersistence(thread => 0, expectedResult = true, calculationTime, eventStoreCreator)
    }

    it should "run concurrently in different transaction scope" taggedAs testsTag in {
      val calculationTime: Int = 1000
      runConcurrentEventPersistence(thread => thread, expectedResult = false, calculationTime, eventStoreCreator)
    }
  }

  def runConcurrentEventPersistence(aggregateIdForThreadF: Int => Int, expectedResult: Boolean, calculationTime: Int,
                                    eventStoreCreator: () => EventStore[TheTestDomainSpecification] with VersionedEventStoreView[TheTestState]): Unit = {
    //given
    val eventStore = eventStoreCreator()
    //and
    val nrOfThreads = 4
    val barriers = new CountDownLatch(nrOfThreads)
    val rollbackError = new AtomicBoolean(false)
    val endThreadsFlag = new AtomicBoolean(false)
    val domainLogic = new TheTestDomainLogic()
    val errorOnExecution = new AtomicReference[Option[Throwable]](None)

    //when 4 thread are executed in parallel to store on a transaction context Map(TestAggregate(1) -> aggregateIdForThreadF(threadNr))
    val service: ExecutorService = Executors.newFixedThreadPool(nrOfThreads)
    (1 to 4) foreach { thread =>
      service.execute(new Runnable {
        override def run(): Unit = {
          var counter = 0
          try {
            while (!Thread.interrupted() && !endThreadsFlag.get() && !rollbackError.get()) {
              val aggregateIdForThread = aggregateIdForThreadF(thread)
              counter = counter + 1
              val scope: AtomicTransactionScope[TheTestDomainSpecification] = eventStore.calculateAtomicTransactionScopeVersion(domainLogic, TestCommandForThreads(thread, aggregateIdForThread)).futureValue.toOption.get
              eventStore.persistEvents(List(TestEventThread(thread, counter)), TestAggregateThread(thread), scope).futureValue match {
                case -\/(error) => error match {
                  case EventSourceCommandRollback() => rollbackError.set(true)
                  case EventSourceCommandFailed(_)  =>
                }
                case \/-(b) =>
              }
            }
          }
          catch {
            case e: InterruptedException =>
              if (endThreadsFlag.get()) {
                // it is ok, because the execution service shutdown
              }
              else {
                errorOnExecution.set(Some(e))
              }
            case e: Throwable => errorOnExecution.set(Some(e))
          }
          finally {
            barriers.countDown()
          }
        }
      })
    }
    barriers.await(calculationTime, TimeUnit.MILLISECONDS)
    endThreadsFlag.set(true)
    val timeToCloseThread = 100
    service.shutdownNow()
    barriers.await(timeToCloseThread, TimeUnit.MILLISECONDS)

    //then a error related to concurrency transaction scopes must be found
    rollbackError.get() shouldBe expectedResult
    errorOnExecution.get() shouldBe empty
  }
}
