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

package eu.pmsoft.mcomponents.eventsourcing.eventstore.sql

import java.util.concurrent.atomic.AtomicInteger

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreAtomicProjectionCreationLogic
import org.scalatest.{ Matchers, fixture }
import scalikejdbc._
import scalikejdbc.config.DBs

import scalaz.\/-

class EventStoreSqlReadOnlyTransactionTest extends fixture.FlatSpec with DBAutoRollback with Matchers {

  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(enabled = false, stackTraceDepth = 3)
  GlobalSettings.loggingSQLErrors = false

  DBs.setup('eventStoreTestDB)

  override def db(): DB = NamedDB('eventStoreTestDB).toDB

  val databaseCounter = new AtomicInteger(0)

  def createDDL(): EventStoreSqlDDL = {
    val ddl = EventStoreSqlDDL.fromDialect(H2EventStoreSqlDialect, s"test${databaseCounter.getAndIncrement()}")
    ddl.createTables(db())
    ddl
  }

  def eventStoreReadOnlyTransaction(db: DB, ddl: EventStoreSqlDDL): EventStoreSqlReadOnlyReadTransaction[SqlTestDomain, SqlTestProjection] = {
    new EventStoreSqlReadOnlyReadTransaction[SqlTestDomain, SqlTestProjection](
      db,
      new EventStoreAtomicProjectionCache(new SqlTestEventStoreAtomicProjectionCreationLogic()),
      new SqlTestEventSerializationSchema(),
      ddl
    )
  }

  def eventStoreWriteTransaction(db: DB, ddl: EventStoreSqlDDL): EventStoreSqlWriteTransaction[SqlTestDomain] = {
    new EventStoreSqlWriteTransaction[SqlTestDomain](db, new SqlTestEventSerializationSchema(), ddl)
  }

  it should "provide empty state on init" in { implicit db =>
    val ddl = createDDL()
    val atomicTransaction = eventStoreReadOnlyTransaction(db, ddl)
    atomicTransaction.eventStoreVersion shouldBe EventStoreVersion.zero
    atomicTransaction.projectionState.eventStoreVersion shouldBe EventStoreVersion.zero
    atomicTransaction.extractEventRange(EventStoreRange(EventStoreVersion.zero, None)) shouldBe empty
  }

  it should "provide correct event store version after persist" in { implicit db =>
    val ddl = createDDL()
    val atomicTransaction = eventStoreWriteTransaction(db, ddl)
    val transactionScope = AtomicTransactionScope[SqlTestDomain](Map(SqlTestAggregate(0) -> 0L), Map(), SqlTestProjectionState(EventStoreVersion.zero))
    val version = atomicTransaction.persistEvents(List(1, 2, 3), SqlTestAggregate(0), transactionScope)
    version shouldBe \/-(EventStoreVersion(3L))
  }

  it should "not update projection or event store version after persist of events" in { implicit db =>
    val ddl = createDDL()
    val readTransaction = eventStoreReadOnlyTransaction(db, ddl)
    readTransaction.eventStoreVersion shouldBe EventStoreVersion.zero

    val atomicTransaction = eventStoreWriteTransaction(db, ddl)
    val transactionScope = AtomicTransactionScope[SqlTestDomain](Map(SqlTestAggregate(0) -> 0L), Map(), SqlTestProjectionState(EventStoreVersion.zero))
    atomicTransaction.persistEvents(List(1, 2, 3), SqlTestAggregate(0), transactionScope)
    atomicTransaction.eventStoreVersion shouldBe EventStoreVersion.zero
    //then
    readTransaction.projectionState.eventStoreVersion shouldBe EventStoreVersion.zero
  }

  it should "store events" in { implicit db =>
    val ddl = createDDL()
    val atomicTransaction = eventStoreWriteTransaction(db, ddl)
    val transactionScope = AtomicTransactionScope[SqlTestDomain](Map(SqlTestAggregate(0) -> 0L), Map(), SqlTestProjectionState(EventStoreVersion.zero))
    atomicTransaction.persistEvents(List(1, 2, 3), SqlTestAggregate(0), transactionScope)

    val readTransaction = eventStoreReadOnlyTransaction(db, ddl)
    readTransaction.extractEventRange(EventStoreRange(EventStoreVersion.zero, None)) should be(Seq(1, 2, 3))
  }

  it should "use event store version according to the transaction status" in { implicit db =>
    val ddl = createDDL()
    //given a transaction instance created events
    val readTransaction = eventStoreReadOnlyTransaction(db, ddl)
    val atomicTransaction = eventStoreWriteTransaction(db, ddl)
    val transactionScope = AtomicTransactionScope[SqlTestDomain](Map(SqlTestAggregate(0) -> 0L), Map(), SqlTestProjectionState(EventStoreVersion.zero))
    val versionAfterUpdate = atomicTransaction.persistEvents(List(1, 2, 3), SqlTestAggregate(0), transactionScope).toOption.get
    readTransaction.extractEventRange(EventStoreRange(EventStoreVersion.zero, None)) should be(Seq(1, 2, 3))
    //when next transaction is created
    val atomicTransaction2 = eventStoreReadOnlyTransaction(db, ddl)
    //then state match the changed values
    atomicTransaction2.eventStoreVersion shouldBe versionAfterUpdate
    atomicTransaction2.projectionState.eventStoreVersion shouldBe versionAfterUpdate
  }

  it should "calculate aggregate versions for a empty database" in { implicit db =>
    val ddl = createDDL()
    //given a transaction instance created events
    val atomicTransaction = eventStoreReadOnlyTransaction(db, ddl)
    //when
    val aggregatesVersion = atomicTransaction.calculateAggregateVersions(Set(SqlTestAggregate(0)))
    //then
    aggregatesVersion should be(Map(SqlTestAggregate(0) -> 0L))
  }

  it should "calculate aggregate versions after events are stored" in { implicit db =>
    val ddl = createDDL()
    //given a transaction instance created events
    val atomicTransaction = eventStoreWriteTransaction(db, ddl)
    val readTransaction = eventStoreReadOnlyTransaction(db, ddl)
    //and the current aggregate version is extracted
    val aggregatesVersion = readTransaction.calculateAggregateVersions(Set(SqlTestAggregate(0)))
    val transactionScope = AtomicTransactionScope[SqlTestDomain](aggregatesVersion, Map(), SqlTestProjectionState(EventStoreVersion.zero))
    //when events are stored
    atomicTransaction.persistEvents(List(1, 2, 3), SqlTestAggregate(0), transactionScope)
    //then the aggregate version change on the next transaction
    val atomicTransaction2 = eventStoreReadOnlyTransaction(db, ddl)
    val aggregatesVersionAfter = atomicTransaction2.calculateAggregateVersions(Set(SqlTestAggregate(0)))
    aggregatesVersionAfter should be(Map(SqlTestAggregate(0) -> 1L))
  }

  it should "rollback transactions on already used transactions scopes" in { implicit db =>
    val ddl = createDDL()
    //given a transaction instance created events
    val atomicTransaction = eventStoreWriteTransaction(db, ddl)
    val readTransaction = eventStoreReadOnlyTransaction(db, ddl)
    //and an aggregate version is used to store values
    val aggregatesVersion = readTransaction.calculateAggregateVersions(Set(SqlTestAggregate(0)))
    val transactionScope = AtomicTransactionScope[SqlTestDomain](aggregatesVersion, Map(), SqlTestProjectionState(EventStoreVersion.zero))
    atomicTransaction.persistEvents(List(1, 2, 3), SqlTestAggregate(0), transactionScope)
    //when the same version is used in other transaction

    val atomicTransaction2 = eventStoreWriteTransaction(db, ddl)
    val secondTransactionResult = atomicTransaction2.persistEvents(List(4), SqlTestAggregate(0), transactionScope)
    //then a exception is returned
    secondTransactionResult.isLeft should be(true)
  }

}

trait SqlTestDomain extends DomainSpecification {

  type Command = Any
  type Event = Int
  type Aggregate = SqlTestAggregate
  type ConstraintScope = SqlConstraintScope
  type State = SqlTestProjection
  type SideEffects = Any
}

trait SqlTestProjection {
  def eventStoreVersion: EventStoreVersion
}

case class SqlTestAggregate(scope: Int)
case class SqlConstraintScope(scope: Int)

case class SqlTestProjectionState(eventStoreVersion: EventStoreVersion) extends SqlTestProjection

class SqlTestEventSerializationSchema extends EventSerializationSchema[SqlTestDomain] {

  //TODO test constraints on sql
  override def buildConstraintReference(constraintScope: SqlConstraintScope): ConstraintReference = ConstraintReference.noConstraintsOnDomain

  override def buildAggregateReference(aggregate: SqlTestAggregate): AggregateReference = AggregateReference(0, aggregate.scope)

  override def eventToData(event: Int): EventData = EventData(Array(event.toByte))

  override def mapToEvent(data: EventDataWithNr): Int = data.eventBytes(0).toInt
}

class SqlTestEventStoreAtomicProjectionCreationLogic extends EventStoreAtomicProjectionCreationLogic[SqlTestDomain, SqlTestProjection] {
  override def buildInitialState(): SqlTestProjection = SqlTestProjectionState(EventStoreVersion.zero)

  override def projectSingleEvent(state: SqlTestProjection, event: Int): SqlTestProjection =
    SqlTestProjectionState(state.eventStoreVersion.add(1))
}
