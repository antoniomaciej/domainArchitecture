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

import eu.pmsoft.mcomponents.eventsourcing.test.model.{ TheTestAggregate, TheTestEvent, TheTestState, TheTestDomainSpecification }
import eu.pmsoft.mcomponents.eventsourcing.{ BindingInfrastructure, EventSourcingConfiguration, EventSerializationSchema }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.ExecutionContext
import scala.reflect._

class EventStoreProvider$Test extends FlatSpec with Matchers with MockFactory {

  it should "throw a illegal state exception when event store is not configured" in {
    val stateCreationLogic = mock[EventStoreAtomicProjectionCreationLogic[TheTestDomainSpecification, TheTestState]]
    val schema = mock[EventSerializationSchema[TheTestDomainSpecification]]
    val eventStoreReference = EventStoreReference[TheTestDomainSpecification](
      EventStoreID("any"),
      classTag[TheTestEvent],
      classTag[TheTestAggregate]
    )
    intercept[IllegalStateException] {
      implicit val emptyConfiguration = EventSourcingConfiguration(ExecutionContext.Implicits.global, mock[BindingInfrastructure], Set())
      EventStoreProvider.createEventStore(stateCreationLogic, schema, eventStoreReference)
    }
  }
}
