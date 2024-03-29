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

package eu.pmsoft.mcomponents.eventsourcing.inmemory

import java.util.concurrent.TimeUnit

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore._
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import eu.pmsoft.mcomponents.test.Mocked
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }
import rx.observers.TestSubscriber
import rx.{ Subscriber, Observable }

import scala.reflect._

class EventProducerTest extends FlatSpec with Matchers with MockFactory {

  it should "bind event store" in {
    //given
    val subscriber: Subscriber[VersionedEvent[TheTestDomainSpecification]] = new TestSubscriber[VersionedEvent[TheTestDomainSpecification]]()
    val producer = new EventProducer(eventStoreReference, EventStoreVersion.zero, subscriber)
    val eventStore: EventStoreRead[TheTestDomainSpecification] = fakeEventStoreDelayed()

    //when
    producer.bindEventStore(eventStore)

    //then
    producer.producerState().status should be(EventStoreBind(eventStore))
  }

  it should "Ignore any second binding" in {
    //given
    val subscriber: Subscriber[VersionedEvent[TheTestDomainSpecification]] = new TestSubscriber[VersionedEvent[TheTestDomainSpecification]]()
    val producer = new EventProducer(eventStoreReference, EventStoreVersion.zero, subscriber)
    val eventStore: EventStoreRead[TheTestDomainSpecification] = fakeEventStoreDelayed()
    val eventStore2: EventStoreRead[TheTestDomainSpecification] = fakeEventStoreDelayed()

    //when
    producer.bindEventStore(eventStore)
    producer.bindEventStore(eventStore2)

    //then
    producer.producerState().status should be(EventStoreBind(eventStore))
  }

  it should "ignore a event store binding if state is not init" in {
      def testStatus(testedStatus: ProducerStatus): Unit = {
        val subscriber: Subscriber[VersionedEvent[TheTestDomainSpecification]] = new TestSubscriber[VersionedEvent[TheTestDomainSpecification]]()
        val producer = new EventProducer(eventStoreReference, EventStoreVersion.zero, subscriber)
        producer.producerState.updateAndGet { state => state.copy(status = testedStatus) }
        val eventStore: EventStoreRead[TheTestDomainSpecification] = fakeEventStoreDelayed()
        //when
        producer.bindEventStore(eventStore)
        //then
        producer.producerState().status should be(testedStatus)
      }

    testStatus(Completed)
    testStatus(Shutdown)
    testStatus(EventStoreBind(fakeEventStoreDelayed()))
    testStatus(ErrorState(new NullPointerException()))

  }

  it should "handle errors on event store observation" in {
    //given
    val subscriber: TestSubscriber[VersionedEvent[TheTestDomainSpecification]] = new TestSubscriber[VersionedEvent[TheTestDomainSpecification]]()
    val producer = new EventProducer(eventStoreReference, EventStoreVersion.zero, subscriber)
    val eventStore: EventStoreRead[TheTestDomainSpecification] = fakeEventStoreWithError()

    //when
    producer.bindEventStore(eventStore)

    //then
    import scala.collection.JavaConversions._
    subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS)
    subscriber.getOnErrorEvents.toList should not be empty
    producer.producerState().status should be(Shutdown)
  }

  it should "Disable backPressure if subscriber request for Long.MaxValue" in {
    //given
    val subscriber: TestSubscriber[VersionedEvent[TheTestDomainSpecification]] = new TestSubscriber[VersionedEvent[TheTestDomainSpecification]]()
    val producer = new EventProducer(eventStoreReference, EventStoreVersion.zero, subscriber)
    val eventStore: EventStoreRead[TheTestDomainSpecification] = fakeEventStore()
    producer.request(Long.MaxValue)
    producer.request(1)

    //when
    producer.bindEventStore(eventStore)

    //then
    producer.producerState().nrOfRequestedEvents should be(Long.MaxValue)
    import scala.collection.JavaConversions._
    subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS)
    subscriber.getOnErrorEvents.toList shouldBe empty
    subscriber.getOnNextEvents.toList should be(List(
      VersionedEvent[TheTestDomainSpecification](EventStoreVersion(1), TestEventOne()),
      VersionedEvent[TheTestDomainSpecification](EventStoreVersion(2), TestEventTwo()),
      VersionedEvent[TheTestDomainSpecification](EventStoreVersion(3), TestEventThree())
    ))
  }

  val eventStoreReference = EventStoreReference[TheTestDomainSpecification](
    EventStoreID("any"),
    classTag[TheTestEvent],
    classTag[TheTestAggregate]
  )

  def fakeEventStoreDelayed(): EventStoreRead[TheTestDomainSpecification] = new EventStoreRead[TheTestDomainSpecification] {

    val events = List(TestEventOne(), TestEventTwo(), TestEventThree())

    override def loadEvents(range: EventStoreRange): Seq[TheTestEvent] = EventStoreRangeUtils.extractRangeFromList(events, range)

    override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Observable.just(EventStoreVersion.zero).delay(3, TimeUnit.SECONDS)

    override def loadEventsForAggregate(aggregate: TheTestAggregate): Seq[TheTestEvent] = Mocked.shouldNotBeCalled
  }
  def fakeEventStore(): EventStoreRead[TheTestDomainSpecification] = new EventStoreRead[TheTestDomainSpecification] {

    val events = List(TestEventOne(), TestEventTwo(), TestEventThree())

    override def loadEvents(range: EventStoreRange): Seq[TheTestEvent] = EventStoreRangeUtils.extractRangeFromList(events, range)

    override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Observable.just(EventStoreVersion.zero, EventStoreVersion(1), EventStoreVersion(2), EventStoreVersion(3))

    override def loadEventsForAggregate(aggregate: TheTestAggregate): Seq[TheTestEvent] = Mocked.shouldNotBeCalled
  }

  def fakeEventStoreWithError(): EventStoreRead[TheTestDomainSpecification] = new EventStoreRead[TheTestDomainSpecification] {

    val events = List(TestEventOne(), TestEventTwo(), TestEventThree())

    override def loadEvents(range: EventStoreRange): Seq[TheTestEvent] = EventStoreRangeUtils.extractRangeFromList(events, range)

    override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Observable.just(EventStoreVersion.zero).mergeWith(Observable.error(new IllegalStateException()))

    override def loadEventsForAggregate(aggregate: TheTestAggregate): Seq[TheTestEvent] = Mocked.shouldNotBeCalled
  }

}
