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

import eu.pmsoft.mcomponents.eventsourcing.{ EventStoreSqlDialect, H2EventStoreSqlDialect, MySqlEventStoreSqlDialect, PostgresEventStoreSqlDialect }
import scalikejdbc._

object EventStoreSqlDDL {

  private val tablesNamespaceRegex = """^([a-zA-Z]{1}[\w]{0,31})$"""

  def fromDialect(dialect: EventStoreSqlDialect, tablesNamespaceConfig: String): EventStoreSqlDDL = {
    val tablesNamespace = tablesNamespaceConfig.matches(tablesNamespaceRegex) match {
      case true  => tablesNamespaceConfig
      case false => throw new IllegalArgumentException(s"Invalid tablesNamespaceConfig name. A keyspace may 32 or fewer alpha-numeric characters and underscores. Value was:$tablesNamespaceConfig")
    }
    dialect match {
      case H2EventStoreSqlDialect       => new H2EventStoreSqlDDL(tablesNamespace)
      case MySqlEventStoreSqlDialect    => new MySqlEventStoreSqlDDL(tablesNamespace)
      case PostgresEventStoreSqlDialect => new PostgresEventStoreSqlDDL(tablesNamespace)
    }
  }
}

sealed trait EventStoreSqlDDL {

  def tablesNamespace: String

  lazy val eventDataTableName = s"${tablesNamespace}_event_data"
  lazy val aggregatesTableName = s"${tablesNamespace}_aggregates"
  lazy val eventDataTableSql = SQLSyntax.createUnsafely(eventDataTableName)
  lazy val aggregatesTableSql = SQLSyntax.createUnsafely(aggregatesTableName)

  def tablesExists(db: DB): Boolean = {
    (for {
      events <- db.getTable(eventDataTableName, Array("TABLE"))
      aggregates <- db.getTable(aggregatesTableName, Array("TABLE"))
    } yield true).isDefined
  }

  def dropTables(db: DB): Unit = {
    db localTx { implicit session =>
      sql"""DROP TABLE IF EXISTS ${eventDataTableSql}""".execute.apply()
      sql"""DROP TABLE IF EXISTS ${aggregatesTableSql}""".execute.apply()
    }
  }

  def createTables(db: DB): Unit
}

private class H2EventStoreSqlDDL(val tablesNamespace: String) extends EventStoreSqlDDL {

  def createTables(db: DB): Unit = {
    db localTx { implicit session =>
      sql"""
            create table ${eventDataTableSql} (
            event_nr BIGINT AUTO_INCREMENT(1,1) primary key, aggregate_type INT, unique_id VARCHAR(255),
            binary_data BLOB,
            created_at timestamp DEFAULT CURRENT_TIMESTAMP() not null )
        """.execute.apply()

      sql"""
            create table ${aggregatesTableSql} (
            aggregate_type INT,
            version BIGINT,
            unique_id VARCHAR(255),
            PRIMARY KEY (aggregate_type,unique_id,version)
         )
        """.execute.apply()
    }
  }
}

private class MySqlEventStoreSqlDDL(val tablesNamespace: String) extends EventStoreSqlDDL {

  def createTables(db: DB): Unit = {
    db localTx { implicit session =>
      sql"""
            create table ${eventDataTableSql} (
            event_nr BIGINT AUTO_INCREMENT primary key, aggregate_type INT, unique_id VARCHAR(255),
            binary_data BLOB,
            created_at timestamp DEFAULT CURRENT_TIMESTAMP() not null )
        """.execute.apply()
      sql"""
            create table ${aggregatesTableSql} (
            aggregate_type INT,
            version BIGINT,
            unique_id VARCHAR(255),
            PRIMARY KEY (aggregate_type,unique_id,version)
         )
        """.execute.apply()
    }
  }
}

private class PostgresEventStoreSqlDDL(val tablesNamespace: String) extends EventStoreSqlDDL {

  def createTables(db: DB): Unit = {
    db localTx { implicit session =>
      sql"""
            create table ${eventDataTableSql} (
            event_nr bigserial primary key , aggregate_type INT, unique_id VARCHAR(255),
            binary_data bytea,
            created_at timestamp DEFAULT now() not null )
        """.execute.apply()
      sql"""
            create table ${aggregatesTableSql} (
            aggregate_type INT,
            version BIGINT,
            unique_id VARCHAR(255),
            PRIMARY KEY (aggregate_type,unique_id,version)
         )
        """.execute.apply()
    }
  }
}
