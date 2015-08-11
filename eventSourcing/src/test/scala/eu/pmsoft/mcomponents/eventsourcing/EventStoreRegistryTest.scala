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

package eu.pmsoft.mcomponents.eventsourcing

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.CommandResult
import eu.pmsoft.mcomponents.test.{BaseEventSourceSpec, Mocked}

import scala.concurrent.Future
import scala.reflect._

class EventStoreRegistryTest extends BaseEventSourceSpec {

  it should "event store match the type contract" in {
    //given
    val testId: EventStoreID = EventStoreID("test1")
    //when a event store is created
    val eventStore = testEventStore(testId)
    //then reference match the type contract
    val expected = EventStoreReference(testId, classTag[TestEventRootType], classTag[TestAggregateRootType])
    expected shouldBe eventStore.reference
  }

  it should "find event store loader after registration" in {
    //given
    val registry = EventStoreRegistry.create()
    val testId: EventStoreID = EventStoreID("test1")
    val eventStore = testEventStore(testId)
    //when the event store is registered and initialized
    registry.registerEventStore(eventStore)
    registry.init should be(\/-)
    //then loader lookup match
    val loading = registry.lookupEventStoreLoad(eventStore.reference)
    loading should be(\/-)
    loading.toOption.get shouldBe eventStore
  }

  it should "find event store storage after registration" in {
    //given
    val registry = EventStoreRegistry.create()
    val testId: EventStoreID = EventStoreID("test1")
    val eventStore = testEventStore(testId)
    //when the event store is registered
    registry.registerEventStore(eventStore)
    registry.init should be(\/-)
    //then loader lookup match
    val storage = registry.lookupEventStoreStorage(eventStore.reference)
    storage should be(\/-)
    storage.toOption.get shouldBe eventStore
  }

  it should "mark and error when no registry is found" in {
    //given
    val registry = EventStoreRegistry.create()
    val testId: EventStoreID = EventStoreID("test1")
    val eventStore = testEventStore(testId)
    //when
    registry.init should be(\/-)
    //then
    registry.lookupEventStoreLoad(eventStore.reference) should be(-\/)
    registry.lookupEventStoreStorage(eventStore.reference) should be(-\/)
  }

  it should "double registration is not possible" in {
    //given
    val registry = EventStoreRegistry.create()
    val testId: EventStoreID = EventStoreID("test1")
    val eventStore = testEventStore(testId)
    //and the event store is registered
    registry.registerEventStore(eventStore)
    registry.init should be(\/-)
    //then the second registration is unsuccessful
    registry.registerEventStore(eventStore)
    registry.init should be(-\/)
  }
  it should "registration depend on event store ID" in {
    //given
    val registry = EventStoreRegistry.create()
    //when
    //then two different event stores with  the same signature but different ID can be registered
    registry.registerEventStore(testEventStore(EventStoreID("test1")))
    registry.registerEventStore(testEventStore(EventStoreID("test2")))
    registry.init should be(\/-)
  }


  private def testEventStore(testId: EventStoreID) = {
    new EventStore[TestEventRootType, TestAggregateRootType] {
      override def reference: EventStoreReference[TestEventRootType, TestAggregateRootType] =
        EventStoreReference(identificationInfo.id, identificationInfo.rootEventType, identificationInfo.aggregateRootType)

      override def loadEvents(range: EventStoreRange): Future[Seq[TestEventRootType]] = Mocked.shouldNotBeCalled

      override def persistEvents(events: List[TestEventRootType],
                                 transactionScopeVersion: Map[TestAggregateRootType, Long])
      : Future[CommandResult] = Mocked.shouldNotBeCalled

      override def identificationInfo: EventStoreIdentification[TestEventRootType, TestAggregateRootType] =
        new EventStoreIdentification[TestEventRootType, TestAggregateRootType] {

          override def aggregateRootType: ClassTag[TestAggregateRootType] = classTag[TestAggregateRootType]

          override def rootEventType: ClassTag[TestEventRootType] = classTag[TestEventRootType]

          override def id: EventStoreID = testId

        }
    }
  }
}

case class TestEventRootType()

case class TestAggregateRootType()
