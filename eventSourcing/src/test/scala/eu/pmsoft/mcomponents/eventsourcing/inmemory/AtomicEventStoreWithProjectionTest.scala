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

package eu.pmsoft.mcomponents.eventsourcing.inmemory

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ CountDownLatch, Executor, Executors, TimeUnit }

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ FlatSpec, Matchers }
import org.typelevel.scalatest.DisjunctionMatchers

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.reflect.{ ClassTag, classTag }
import scalaz._

class AtomicEventStoreWithProjectionTest extends FlatSpec with Matchers with PropertyChecks with ScalaFutures with DisjunctionMatchers {

  def addOneEvent(eventStore: EventStore[TestDomainSpecification] with VersionedEventStoreView[TestAggregate, TestState])(eventNr: Int): Unit = {
    val transactionScope = eventStore.projection(Set(TestAggregate(1))).futureValue
    eventStore.persistEvents(List(TestEvent(eventNr)), transactionScope.transactionScopeVersion).futureValue shouldBe \/-
  }

  it should "reuse pending futures load " in {
    //given
    implicit val eventSourceExecutionContext = testExecutionContext()
    //and a event store is created
    val eventStore = createTestEventStore()
    //when a range is extracted two times
    val range = EventStoreRange(EventStoreVersion(2L), None)
    val loadFuture1 = eventStore.loadEvents(range)
    val loadFuture2 = eventStore.loadEvents(range)
    //and the events are stored
    val addFunc: (Int) => Unit = addOneEvent(eventStore)
    (0 to 4).foreach(addFunc)
    //then the same value is returned, because a common promise was used
    loadFuture1.futureValue shouldBe loadFuture2.futureValue
  }

  it should "provide projections on the future" in {

    //given
    implicit val eventSourceExecutionContext = testExecutionContext()
    //and a event store is created
    val eventStore: EventStore[TestDomainSpecification] with VersionedEventStoreView[TestAggregate, TestState] = createTestEventStore()
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
    futureStateOne.futureValue.events shouldBe List(TestEvent(1), TestEvent(2), TestEvent(3), TestEvent(4))
    //and future events do not change the projections retrieved
    addFunc(5)
    futureStateTwo.futureValue.events shouldBe List(TestEvent(1), TestEvent(2), TestEvent(3), TestEvent(4))
    //but if in the future the atLeastOn operations is used with a version number that is in the pass
    val afterAll = eventStore.atLeastOn(versionToExtract)
    //then the last version is returned
    afterAll.futureValue.events shouldBe List(TestEvent(1), TestEvent(2), TestEvent(3), TestEvent(4), TestEvent(5))
    //and this match the range [1,inf)
    eventStore.loadEvents(EventStoreRange(EventStoreVersion(1L), None)).futureValue shouldBe List(TestEvent(1), TestEvent(2), TestEvent(3), TestEvent(4), TestEvent(5))
  }

  it should "range [2,4) is correctly extracted" in {
    //given
    implicit val eventSourceExecutionContext = testExecutionContext()
    //and a event store is created
    val eventStore = createTestEventStore()
    //and a number of events to generate
    val nrOfEvents = 10
    //and a future to load range [2,4) is set before the events creation
    val futureLoad1To4BeforeEvents = eventStore.loadEvents(EventStoreRange(EventStoreVersion(2L), Some(EventStoreVersion(4L))))

    //when events are added
    val addFunc: (Int) => Unit = addOneEvent(eventStore)
    (1 to nrOfEvents).foreach(addFunc)
    //then the preLoad call contains only one element
    futureLoad1To4BeforeEvents.futureValue shouldBe List(TestEvent(2))
    //and a load after the events provide all the available events
    val futureLoad1To4AfterEvents = eventStore.loadEvents(EventStoreRange(EventStoreVersion(2L), Some(EventStoreVersion(4L))))
    futureLoad1To4AfterEvents.futureValue shouldBe List(TestEvent(2), TestEvent(3))
  }

  it should "provide events for the load api in the future" in {
    //given
    implicit val eventSourceExecutionContext = testExecutionContext()
    //and a event store is created
    val eventStore = createTestEventStore()
    //and a version range to extract
    val versionNumber = 4L
    //and two future are setup on ranges [4,inf), [3,inf)
    val futureLoadOn3 = eventStore.loadEvents(EventStoreRange(EventStoreVersion(versionNumber - 1), None))
    val futureLoadOn4 = eventStore.loadEvents(EventStoreRange(EventStoreVersion(versionNumber), None))

    //when events are added
    val addFunc: (Int) => Unit = addOneEvent(eventStore)
    (1 to 3).foreach(addFunc)

    //then the event store version is 3 and
    futureLoadOn3.futureValue should be(Seq(TestEvent(3)))
    futureLoadOn4.isCompleted should be(false)

    //when add one more event
    addFunc(4)

    //then load on 4 will finish
    futureLoadOn4.futureValue should be(Seq(TestEvent(4)))

  }

  it should "detect concurrent execution of updates in the same transaction scope" in {
    //given
    implicit val eventSourceExecutionContext = testExecutionContext()
    //when a event store is created
    val eventStore = createTestEventStore()
    //and
    val nrOfThreads = 4
    val barriers = new CountDownLatch(nrOfThreads)
    val rollbackError = new AtomicBoolean(false)

    //when 4 thread are executed in parallel to store on the same transaction context Map(TestAggregate(1) -> 0L)
    val service: Executor = Executors.newFixedThreadPool(nrOfThreads)
    (1 to 4) foreach { thread =>
      service.execute(new Runnable {
        override def run(): Unit = {
          while (!rollbackError.get()) {
            val transactionScope = eventStore.projection(Set(TestAggregate(1))).futureValue
            eventStore.persistEvents(List(TestEvent(0)), transactionScope.transactionScopeVersion).futureValue match {
              case -\/(error) => error match {
                case EventSourceCommandRollback() => rollbackError.set(true)
                case EventSourceCommandFailed(_)  =>
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
    val eventStore = createTestEventStore()
    val nrOfThreads = 4
    val barriers = new CountDownLatch(nrOfThreads)
    val rollbackError = new AtomicBoolean(false)

    //when 4 thread are executed in parallel to store on A DIFFERENT TRANSACTION SCOPE
    val service: Executor = Executors.newFixedThreadPool(nrOfThreads)
    (1 to 4) foreach { thread =>
      service.execute(new Runnable {
        override def run(): Unit = {
          while (!rollbackError.get()) {
            val transactionScope = eventStore.projection(Set(TestAggregate(thread))).futureValue
            eventStore.persistEvents(List(TestEvent(0)), transactionScope.transactionScopeVersion) futureValue match {
              case -\/(error) => error match {
                case EventSourceCommandRollback() => rollbackError.set(true)
                case EventSourceCommandFailed(_)  =>
              }
              case \/-(b) =>
            }
          }
          barriers.countDown()
        }
      })
    }

    barriers.await(1, TimeUnit.SECONDS)
    //then all transactions pass
    rollbackError.get() shouldBe false
  }

  private def testExecutionContext() = {
    implicit val eventSourcingConfiguration = EventSourcingConfiguration(ExecutionContext.global, LocalBindingInfrastructure.create())
    EventSourceExecutionContextProvider.create()
  }

  def createTestEventStore(): EventStore[TestDomainSpecification] with VersionedEventStoreView[TestAggregate, TestState] = {
    val identifier = new EventStoreIdentification[TestDomainSpecification] {
      override def id: EventStoreID = EventStoreID("NoOpEventStoreInMemory")

      override def aggregateRootType: ClassTag[TestAggregate] = classTag[TestAggregate]

      override def rootEventType: ClassTag[TestEvent] = classTag[TestEvent]
    }
    EventStoreProvider.createEventStore[TestDomainSpecification, TestState](new NoOpEventStoreAtomicProjection(), identifier)
  }

  class NoOpEventStoreAtomicProjection extends EventStoreAtomicProjection[TestEvent, TestState] {
    override def buildInitialState(): TestState = TestState()

    override def projectSingleEvent(state: TestState, event: TestEvent): TestState = {
      state.copy(events = state.events :+ event)
    }
  }

  class TestDomainSpecification extends DomainSpecification {
    type Command = Any
    type Event = TestEvent
    type Aggregate = TestAggregate
    type State = TestState
    type SideEffects = Any
  }

  case class TestAggregate(scope: Int)

  case class TestEvent(anyData: Int)

  case class TestState(events: List[TestEvent] = List())

}

