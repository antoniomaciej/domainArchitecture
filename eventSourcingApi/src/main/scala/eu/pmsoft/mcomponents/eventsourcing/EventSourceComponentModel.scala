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

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

/**
 * Define the set of components that are composed to create a event sourcing application.
 */
object EventSourceComponentModel {

}

/**
 * A global configuration that contain all external dependencies necessary to run the event source application.
 * @param executionContext the ExecutionContext to run Future calculations.
 * @param bindingInfrastructure the BindingInfrastructure that implements integration between event stores and projections
 */
case class EventSourcingConfiguration(executionContext: ExecutionContext, bindingInfrastructure: BindingInfrastructure)

/**
 * A summary of all registered component.
 * @param elements list of references to all components.
 */
case class ExecutionContextStatus(elements: Seq[EventSourceComponentReference], errors: List[EventSourceInitializationError])

/**
 * Sealed trait to list all type of event sourcing components.
 */
sealed trait EventSourceComponentReference

/**
 * A reference to a exiting event store.
 * Note that the event store may be running locally or remotely.
 * For the same type of events and aggregated many event stores can be instantiated.
 * @param id A unique id to identify the event store instance
 * @param eventRootType the root type of the events hierarchy
 * @param aggregateRootType the root type of the aggregates hierarchy
 * @tparam E event root type
 * @tparam A aggregate root type
 */
case class EventStoreReference[E, A](id: EventStoreID,
                                     eventRootType: ClassTag[E],
                                     aggregateRootType: ClassTag[A]) extends EventSourceComponentReference

case class EventStoreID(val id: String) extends AnyVal

/**
 * A projection of a event store.
 * @param source a reference to the source event store.
 * @tparam E event root type
 * @tparam A aggregate root type
 */
case class EventSourceProjectionReference[E, A](source: EventStoreReference[E, A]) extends EventSourceComponentReference

/**
 * A domain application identifier.
 * A domain application provide access to the internal event store state by execution of the related commands.
 * @param domainID unique domain identifier name
 */
case class EventSourceDomainApplication(domainID: EventSourceDomainID) extends EventSourceComponentReference

case class EventSourceDomainID(val name: String) extends AnyVal


sealed trait EventSourceInitializationError

case class EventStoreAlreadyRegistered[E, A](reference: EventStoreReference[E, A]) extends EventSourceInitializationError

case class EventStoreNotRegistered[E, A](reference: EventStoreReference[E, A]) extends EventSourceInitializationError

sealed trait EventSourceInitializationConfirmation

case class EventSourceInitializationConfirmed(status: ExecutionContextStatus) extends EventSourceInitializationConfirmation


