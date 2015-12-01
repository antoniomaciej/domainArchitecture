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

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreAtomicProjectionCreationLogic, EventStoreReadTransaction, EventStoreTransactionalBackend }
import scalikejdbc._

import scalaz.{ -\/, \/- }

class EventStoreSqlAtomicProjection[D <: DomainSpecification, P <: D#State](
    val stateCreationLogic: EventStoreAtomicProjectionCreationLogic[D, P],
    val schema:             EventSerializationSchema[D],
    val backendConfig:      EventStoreSqlBackend[D]
) extends EventStoreTransactionalBackend[D, P] with LoanPattern {

  private[this] val stateCache = new EventStoreAtomicProjectionCache(stateCreationLogic)
  private val ddl = EventStoreSqlDDL.fromDialect(backendConfig.dialect, backendConfig.tablesNamespace)

  override def initializeBackend(): Unit = {
    withDb { db =>
      val exist = ddl.tablesExists(db)
      val drop = backendConfig.rebuildDDL && exist
      val create = drop || !exist
      if (drop) {
        ddl.dropTables(db)
      }
      if (create) {
        ddl.createTables(db)
      }
    }
  }

  override def readOnly[A](execution: (EventStoreReadTransaction[D, P]) => A): A = {
    withDbTransaction { db =>
      execution(new EventStoreSqlReadOnlyReadTransaction(db, stateCache, schema, ddl))
    }
  }

  override def persistEventsOnAtomicTransaction(events: List[D#Event], rootAggregate: D#Aggregate, transactionScopeVersion: Map[D#Aggregate, Long]): CommandResult = {
    withDbTransaction { db =>
      new EventStoreSqlWriteTransaction(db, schema, ddl).persistEvents(events, rootAggregate, transactionScopeVersion) match {
        case -\/(a)       => -\/(EventSourceCommandRollback())
        case \/-(version) => \/-(EventSourceCommandConfirmation(version))
      }
    }
  }

  private def withDbTransaction[A](f: DB => A): A = {
    using(backendConfig.connectionPool.borrow()) { conn: java.sql.Connection =>
      val db: DB = DB(conn)
      db.begin()
      db.autoClose(false)
      try {
        f(db)
      }
      finally {
        db.commit()
      }
    }
  }
  private def withDb[A](f: DB => A): A = {
    using(backendConfig.connectionPool.borrow()) { conn: java.sql.Connection =>
      val db: DB = DB(conn)
      db.autoClose(false)
      f(db)
    }
  }
}

trait SqlExecutionContext {
  def connectionPool: ConnectionPool
}

