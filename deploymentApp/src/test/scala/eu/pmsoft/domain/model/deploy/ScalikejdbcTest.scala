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

package eu.pmsoft.domain.model.deploy

import org.scalatest.{ FlatSpec, Matchers }
import scalikejdbc._
import scalikejdbc.config._

class ScalikejdbcTest extends FlatSpec with Matchers {

  it should "execute simple sqls" in {

    // initialize JDBC driver & connection pool
    DBs.setupAll()

    // ad-hoc session provider on the REPL
    implicit val session = AutoSession

    // table creation, you can run DDL by using #execute as same as JDBC
    sql"""create table members (id serial not null primary key,name varchar(64),created_at timestamp not null)""".execute.apply()

    // insert initial data
    Seq("Alice", "Bob", "Chris") foreach { name =>
      sql"insert into members (name, created_at) values ($name, current_timestamp)".update.apply()
    }

    // for now, retrieves all data as Map value
    val entities: List[Map[String, Any]] = sql"select * from members".map(_.toMap).list.apply()

    // find all members
    val members: List[Member] = sql"select * from members".map(rs => Member(rs)).list.apply()

    // use paste mode (:paste) on the Scala REPL
    val m = Member.syntax("m")
    val name = "Alice"
    val alice: Option[Member] = withSQL {
      select.from(Member as m).where.eq(m.name, name)
    }.map(rs => Member(rs)).single.apply()

    alice should not be empty
    alice.get.name.get should be("Alice")
  }

}

// defines entity object and extractor

import org.joda.time._

case class Member(id: Long, name: Option[String], createdAt: DateTime)

object Member extends SQLSyntaxSupport[Member] {
  override val tableName = "members"

  def apply(rs: WrappedResultSet): Member = new Member(
    rs.long("id"), rs.stringOpt("name"), rs.jodaDateTime("created_at")
  )
}
