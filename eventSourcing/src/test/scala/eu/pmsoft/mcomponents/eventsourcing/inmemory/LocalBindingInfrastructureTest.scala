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

import eu.pmsoft.mcomponents.eventsourcing.{ EventStoreRange, VersionedEvent, EventStoreVersion }
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreRangeUtils, EventStoreRead, EventStoreID, EventStoreReference }
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import eu.pmsoft.mcomponents.test.Mocked
import org.scalatest.{ FlatSpec, Matchers }
import rx.Observable
import rx.observers.TestSubscriber

import scala.reflect._

class LocalBindingInfrastructureTest extends FlatSpec with Matchers {

  it should "bind event stores to streams" in {
    //given
    val localBinding = LocalBindingInfrastructure.create()
    //and a eventStore is registered
    val eventStore = fakeEventStore()
    localBinding.producerApi.registerEventStore(eventStoreReference, eventStore)

    //when a stream is created
    val eventStoreStream: Observable[VersionedEvent[TheTestDomainSpecification]] = localBinding.consumerApi.eventStoreStream(eventStoreReference, EventStoreVersion.zero)

    //then
    val testSubscriber = new TestSubscriber[VersionedEvent[TheTestDomainSpecification]]()
    eventStoreStream.subscribe(testSubscriber)
    testSubscriber.awaitTerminalEvent(2, TimeUnit.SECONDS)

    import scala.collection.JavaConversions._
    testSubscriber.getOnCompletedEvents.toList should not be empty
    testSubscriber.getOnErrorEvents shouldBe empty
    testSubscriber.getOnNextEvents.toList should be(
      List(
        VersionedEvent[TheTestDomainSpecification](EventStoreVersion(1), TestEventOne()),
        VersionedEvent[TheTestDomainSpecification](EventStoreVersion(2), TestEventTwo()),
        VersionedEvent[TheTestDomainSpecification](EventStoreVersion(3), TestEventThree())
      )
    )
  }

  val eventStoreReference = EventStoreReference[TheTestDomainSpecification](
    EventStoreID("any"),
    classTag[TheTestEvent],
    classTag[TheTestAggregate]
  )

  def fakeEventStore(): EventStoreRead[TheTestDomainSpecification] = new EventStoreRead[TheTestDomainSpecification] {

    val events = List(TestEventOne(), TestEventTwo(), TestEventThree())

    override def loadEvents(range: EventStoreRange): Seq[TheTestEvent] = EventStoreRangeUtils.extractRangeFromList(events, range)

    override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Observable.just(EventStoreVersion.zero)

    override def loadEventsForAggregate(aggregate: TheTestAggregate): Seq[TheTestEvent] = Mocked.shouldNotBeCalled
  }
}
