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

import eu.pmsoft.mcomponents.eventsourcing.inmemory.{AtomicEventStoreWithProjectionInMemory, EventStoreWithProjectionInMemoryLogic}

import scala.concurrent.ExecutionContext
import scalaz.{\/-, -\/, \/}

object EventSourceExecutionContextProvider {

  def create()(implicit configuration: EventSourcingConfiguration): EventSourceExecutionContext = new EventSourceExecutionContextImpl()
}


//Implementation

class EventSourceExecutionContextImpl()(implicit val configuration: EventSourcingConfiguration) extends EventSourceExecutionContext {

  lazy val internalRegistry = EventStoreRegistry.create()

  override def init: Seq[EventSourceInitializationError] \/ EventSourceInitializationConfirmation = internalRegistry.init

  override def executionContext: ExecutionContext = configuration.executionContext

  override def componentsSummary(): ExecutionContextStatus = internalRegistry.summary()

  override def createInMemoryEventStore[E, A, S](logic: EventStoreWithProjectionInMemoryLogic[E, A, S],
                                                 identificationInfo: EventStoreIdentification[E, A]): EventStore[E, A] with VersionedEventStoreView[A, S] = {
    val store = new AtomicEventStoreWithProjectionInMemory(logic, identificationInfo)(this)
    internalRegistry.registerEventStore(store)
    store
  }

  override def registerProjection[E, A](projection: EventSourceProjection[E], eventStoreReference: EventStoreReference[E, A]): Unit =
  internalRegistry.lookupEventStoreLoad(eventStoreReference) match {
    case -\/(a) => ???
    case \/-(eventStoreLoad) =>configuration.bindingInfrastructure.bind(projection, eventStoreLoad)
  }

}


trait EventSourceExecutionContextProvided {

  def eventSourceExecutionContext: EventSourceExecutionContext

  final implicit def executionContext: ExecutionContext = eventSourceExecutionContext.executionContext
}
