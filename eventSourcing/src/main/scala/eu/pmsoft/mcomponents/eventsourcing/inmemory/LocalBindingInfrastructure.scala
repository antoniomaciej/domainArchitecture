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

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreRead, EventStoreReference }
import rx.Observable.OnSubscribe
import rx.schedulers.Schedulers
import rx.{ Observable, Producer, Subscriber }

import scalaz.{ -\/, \/, \/- }

object LocalBindingInfrastructure {
  def create(): BindingInfrastructure = new LocalBindingInfrastructure()
}

class LocalBindingInfrastructure extends BindingInfrastructure with EventConsumerInfrastructure with EventProductionInfrastructure {
  override def consumerApi: EventConsumerInfrastructure = this

  override def producerApi: EventProductionInfrastructure = this

  private[this] val eventStoreRegistration = Atomic(BindingRegistrationState())

  override def eventStoreStream[D <: DomainSpecification](
    eventStoreReference: EventStoreReference[D],
    startVersion:        EventStoreVersion
  ): Observable[VersionedEvent[D]] =
    Observable.create[VersionedEvent[D]](new OnSubscribe[VersionedEvent[D]] {
      override def call(subscriber: Subscriber[_ >: VersionedEvent[D]]): Unit = {
        val producer = new EventProducer[D](eventStoreReference, startVersion, subscriber)
        subscriber.setProducer(producer)
        eventStoreRegistration.updateAndGet { registration =>
          registration.registerProducer(eventStoreReference, producer)
        }
      }
    }).subscribeOn(Schedulers.immediate())
      .observeOn(Schedulers.computation())

  override def registerEventStore[D <: DomainSpecification](
    eventStoreReference: EventStoreReference[D],
    eventStore:          EventStoreRead[D]
  ): Unit = {
    eventStoreRegistration.updateAndGet { registration =>
      registration.registerEventStore(eventStoreReference, eventStore)
    }
  }
}

case class BindingRegistrationState(
    eventStoresByRef: Map[EventStoreReference[_], EventStoreRead[_]]     = Map(),
    openStreams:      Map[EventStoreReference[_], Set[EventProducer[_]]] = Map()
) {
  def registerProducer[D <: DomainSpecification](eventStoreReference: EventStoreReference[D], producer: EventProducer[D]): BindingRegistrationState = {
    val updatedProducers = this.openStreams.getOrElse(eventStoreReference, Set()) + producer
    val updatedOpenStreams = this.openStreams.updated(eventStoreReference, updatedProducers)
    eventStoresByRef.get(eventStoreReference).foreach(eventStore =>
      producer.bindEventStore(eventStore.asInstanceOf[EventStoreRead[D]]))
    BindingRegistrationState(eventStoresByRef, updatedOpenStreams)
  }

  def registerEventStore[D <: DomainSpecification](reference: EventStoreReference[D], eventStoreRead: EventStoreRead[D]): BindingRegistrationState = {
    val updatedEventStoresByRef = this.eventStoresByRef.updated(reference, eventStoreRead)
    this.openStreams.getOrElse(reference, Set()).foreach { producer =>
      producer.asInstanceOf[EventProducer[D]].bindEventStore(eventStoreRead)
    }
    BindingRegistrationState(updatedEventStoresByRef, this.openStreams)
  }
}
