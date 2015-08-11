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

import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic

import scalaz._


object EventStoreRegistry {

  def create(): EventStoreRegistry = new LocalEventStoreRegistry()
}

trait EventStoreRegistry extends EventSourceInitializationCycle {

  def registerEventStore[E, A](store: EventStore[E, A]): Unit

  def summary(): ExecutionContextStatus


  def lookupEventStoreStorage[E, A](reference: EventStoreReference[E, A])
  : \/[EventSourceInitializationError, AsyncEventStore[E, A]]

  def lookupEventStoreLoad[E, A](reference: EventStoreReference[E, A])
  : \/[EventSourceInitializationError, EventStoreLoad[E]]
}


class LocalEventStoreRegistry extends EventStoreRegistry {

  val registryState = Atomic(new LocalEventStoreRegistryState())

  override def registerEventStore[E, A](store: EventStore[E, A]): Unit = {
    def updateState(state: LocalEventStoreRegistryState): LocalEventStoreRegistryState = {
      checkThatEventStoreIsNotRegistered(state) match {
        case -\/(error) => state.copy(errors = error :: state.errors)
        case \/-(okState) => okState.copy(stores = store :: okState.stores, storesReferences = store.reference :: okState.storesReferences)
      }
    }

    def checkThatEventStoreIsNotRegistered(state: LocalEventStoreRegistryState): \/[EventSourceInitializationError, LocalEventStoreRegistryState] = {
      state.storesReferences.find { ref => store.reference == ref } match {
        case Some(reference) => -\/(EventStoreAlreadyRegistered(reference))
        case None => \/-(state)
      }
    }
    registryState.updateAndGet(updateState)
  }

  override def lookupEventStoreStorage[E, A](reference: EventStoreReference[E, A])
  : \/[EventSourceInitializationError, AsyncEventStore[E, A]] =
    registryState().stores.find { es => es.reference == reference } match {
      case Some(x) => \/-(x.asInstanceOf[AsyncEventStore[E, A]])
      case None => -\/(EventStoreNotRegistered(reference))
    }

  override def lookupEventStoreLoad[E, A](reference: EventStoreReference[E, A])
  : \/[EventSourceInitializationError, EventStoreLoad[E]] =
    registryState().stores.find { es => es.reference == reference } match {
      case Some(x) => \/-(x.asInstanceOf[EventStoreLoad[E]])
      case None => -\/(EventStoreNotRegistered(reference))
    }


  override def summary(): ExecutionContextStatus = createStatusReport(registryState())

  private def createStatusReport(state: LocalEventStoreRegistryState): ExecutionContextStatus =
    ExecutionContextStatus(state.stores.map(_.reference), state.errors)

  override def init: Seq[EventSourceInitializationError] \/ EventSourceInitializationConfirmation = {
    val state = registryState()
    if (state.errors.isEmpty) {
      \/-(EventSourceInitializationConfirmed(createStatusReport(state)))
    } else {
      -\/(state.errors)
    }
  }
}

case class LocalEventStoreRegistryState(stores: List[EventStore[_, _]] = List(),
                                        storesReferences: List[EventStoreReference[_, _]] = List(),
                                        errors: List[EventSourceInitializationError] = List())
