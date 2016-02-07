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

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import eu.pmsoft.mcomponents.test.DbIntegrationTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ FlatSpec, Matchers }
import org.typelevel.scalatest.DisjunctionMatchers
import scalikejdbc._
import scalikejdbc.config.DBs

import scala.concurrent.ExecutionContext
import scala.reflect._

class EventStoreProviderIntegrationTest extends FlatSpec with Matchers with PropertyChecks with ScalaFutures with DisjunctionMatchers with EventStoreWithVersionedEventStoreViewBehaviour {

  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(enabled = true, stackTraceDepth = 5)
  GlobalSettings.loggingSQLErrors = true
  DBs.setupAll()

  val schema = new TheTestEventSerializationSchema()
  val eventStoreReferenceMySql: EventStoreReference[TheTestDomainSpecification] =
    EventStoreReference[TheTestDomainSpecification](EventStoreID("MySQLIntegration"), classTag[TheTestEvent], classTag[TheTestAggregate])

  val eventStoreReferencePostgresSql: EventStoreReference[TheTestDomainSpecification] =
    EventStoreReference[TheTestDomainSpecification](EventStoreID("PostgresIntegration"), classTag[TheTestEvent], classTag[TheTestAggregate])

  implicit val eventSourcingConfiguration = EventSourcingConfiguration(
    ExecutionContext.global,
    LocalBindingInfrastructure.create(),
    Set(
      EventStoreSqlBackend(eventStoreReferenceMySql, 'MySQLIntegration, MySqlEventStoreSqlDialect, "testEventStore", rebuildDDL = true),
      EventStoreSqlBackend(eventStoreReferencePostgresSql, 'PostgresIntegration, PostgresEventStoreSqlDialect, "testEventStore", rebuildDDL = true)
    )
  )

  def mySqlEventStoreWithProjection(): EventStore[TheTestDomainSpecification] with VersionedEventStoreView[TheTestAggregate, TheTestState] = {
    EventStoreProvider.createEventStore[TheTestDomainSpecification, TestStateInMemory](new TheAtomicProjectionLogic(), schema, eventStoreReferenceMySql)
  }

  def postgresqlBasedEventStoreWithProjection(): EventStore[TheTestDomainSpecification] with VersionedEventStoreView[TheTestAggregate, TheTestState] = {
    EventStoreProvider.createEventStore[TheTestDomainSpecification, TestStateInMemory](new TheAtomicProjectionLogic(), schema, eventStoreReferencePostgresSql)
  }

  "Sql MySql implementation of the event store " should behave like eventStoreWithAtomicProjection(DbIntegrationTest, mySqlEventStoreWithProjection)

  "Sql Postgres implementation of the event store " should behave like eventStoreWithAtomicProjection(DbIntegrationTest, postgresqlBasedEventStoreWithProjection)

}

