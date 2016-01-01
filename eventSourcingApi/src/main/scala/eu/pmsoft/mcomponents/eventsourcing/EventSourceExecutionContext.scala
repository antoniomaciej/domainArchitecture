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

import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreReference
import scalikejdbc.ConnectionPool

import scala.concurrent.ExecutionContext

trait EventSourceExecutionContext extends EventSourcingConfigurationContext {

  def assemblyDomainApplication[D <: DomainSpecification](domainImplementation: DomainModule[D]): DomainCommandApi[D]

}

trait EventSourcingConfigurationContext {
  implicit def eventSourcingConfiguration: EventSourcingConfiguration
}

case class EventSourcingConfiguration(
  executionContext:      ExecutionContext,
  bindingInfrastructure: BindingInfrastructure,
  backendStrategies:     Set[EventStoreBackendStrategy[_]]
)

sealed trait EventStoreBackendStrategy[D <: DomainSpecification] {
  def eventStoreReference: EventStoreReference[D]
}

case class EventStoreInMemory[D <: DomainSpecification](eventStoreReference: EventStoreReference[D]) extends EventStoreBackendStrategy[D]

case class EventStoreSqlBackend[D <: DomainSpecification](
  eventStoreReference: EventStoreReference[D],
  connectionPool:      ConnectionPool,
  dialect:             EventStoreSqlDialect,
  tablesNamespace:     String,
  rebuildDDL:          Boolean                = false
) extends EventStoreBackendStrategy[D]

sealed trait EventStoreSqlDialect
case object H2EventStoreSqlDialect extends EventStoreSqlDialect
case object MySqlEventStoreSqlDialect extends EventStoreSqlDialect
case object PostgresEventStoreSqlDialect extends EventStoreSqlDialect
