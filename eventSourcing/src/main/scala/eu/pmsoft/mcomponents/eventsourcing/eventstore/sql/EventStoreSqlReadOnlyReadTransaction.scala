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

import java.sql.SQLException
import java.util.concurrent.{ Callable, TimeUnit }

import com.google.common.cache.{ Cache, CacheBuilder }
import com.typesafe.scalalogging.LazyLogging
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreAtomicProjectionCreationLogic, EventStoreReadTransaction }
import scalikejdbc._

import scala.collection.immutable.SortedSet
import scalaz._

trait VersionedSqlTransaction {
  def db: DB

  def ddlDialect: EventStoreSqlDDL

  lazy val eventStoreVersion: EventStoreVersion = {
    db.withinTx { implicit session =>
      val version: Option[Long] = sql"""select max(event_nr) from ${ddlDialect.eventDataTableSql}""".map(rs => rs.longOpt(1)).single().apply().flatten
      version.map(v => EventStoreVersion(v)).getOrElse(EventStoreVersion.zero)
    }
  }

}

class EventStoreSqlWriteTransaction[D <: DomainSpecification](
    val db:         DB,
    val schema:     EventSerializationSchema[D],
    val ddlDialect: EventStoreSqlDDL
) extends VersionedSqlTransaction {

  def persistEvents(events: List[D#Event], rootAggregate: D#Aggregate, transactionScopeVersion: Map[D#Aggregate, Long]): Throwable \/ EventStoreVersion = {
    val initialVersion: Long = eventStoreVersion.storeVersion
    val rootAggregateReference = schema.buildReference(rootAggregate)
    db.withinTx { implicit session =>
      try {
        transactionScopeVersion.foreach {
          case (aggregate, version) =>
            val aggregateRef = schema.buildReference(aggregate)
            sql"""insert into ${ddlDialect.aggregatesTableSql} (aggregate_type,unique_id,version) values (${aggregateRef.aggregateType},${aggregateRef.aggregateUniqueId},${version + 1})""".update().apply()
        }
        val lastVersion = (initialVersion /: events) {
          case (version, event) =>
            val eventData = schema.eventToData(event)
            sql"""insert into ${ddlDialect.eventDataTableSql} (binary_data,aggregate_type,unique_id)
                 values (${eventData.eventBytes},${rootAggregateReference.aggregateType},${rootAggregateReference.aggregateUniqueId})"""
              .updateAndReturnGeneratedKey().apply()
        }
        \/-(EventStoreVersion(lastVersion))
      }
      catch {
        case sqlException: SQLException => -\/(sqlException)
      }
    }
  }
}

class EventStoreProjectionOrdering[D <: DomainSpecification, P <: D#State] extends scala.Ordering[AtomicEventStoreStateSql[D, P]] {
  override def compare(x: AtomicEventStoreStateSql[D, P], y: AtomicEventStoreStateSql[D, P]): Int =
    (y.version.storeVersion - x.version.storeVersion).toInt
}

case class OrderedProjectionVersionCache[D <: DomainSpecification, P <: D#State](
    maxSize:  Int,
    versions: SortedSet[AtomicEventStoreStateSql[D, P]]
) {

  def pushState(newState: AtomicEventStoreStateSql[D, P])(implicit versionOrdering: scala.Ordering[AtomicEventStoreStateSql[D, P]]): OrderedProjectionVersionCache[D, P] = {
    val updatedVersions = (this.versions + newState).take(this.maxSize)
    this.copy(versions = updatedVersions)
  }
}

class EventStoreAtomicProjectionCache[D <: DomainSpecification, P <: D#State](val stateCreationLogic: EventStoreAtomicProjectionCreationLogic[D, P]) extends LazyLogging {

  private def stateZero = AtomicEventStoreStateSql[D, P](EventStoreVersion.zero, stateCreationLogic.buildInitialState())

  private implicit val versionOrdering = new EventStoreProjectionOrdering[D, P]()
  //TODO magic number
  private val projectionCacheSize = 20
  private[this] val versionCache = Atomic(OrderedProjectionVersionCache[D, P](projectionCacheSize, SortedSet()))
  private[this] val timedCache: Cache[Long, AtomicEventStoreStateSql[D, P]] =
    CacheBuilder
      .newBuilder()
      .expireAfterAccess(5, TimeUnit.SECONDS)
      .asInstanceOf[CacheBuilder[Long, AtomicEventStoreStateSql[D, P]]]
      .build()

  private def updateStoredCache(newState: AtomicEventStoreStateSql[D, P]) = {
      def updateStoreLocal(inStore: OrderedProjectionVersionCache[D, P]): OrderedProjectionVersionCache[D, P] = {
        val updatedVersions = (inStore.versions + newState).take(inStore.maxSize)
        inStore.copy(versions = updatedVersions)
      }
    versionCache.updateAndGet(updateStoreLocal)
  }

  def buildState(eventStoreVersion: EventStoreVersion, rangeExtractor: EventStoreRange => Seq[D#Event]): P = {
    val cachedState = timedCache.get(eventStoreVersion.storeVersion, new Callable[AtomicEventStoreStateSql[D, P]] {
      override def call(): AtomicEventStoreStateSql[D, P] = {
        val storedVersions = versionCache()
        val nearestStored = storedVersions.versions.filter(_.version.storeVersion <= eventStoreVersion.storeVersion).headOption
        val buildFromState = nearestStored.getOrElse(stateZero)
        if (buildFromState.version.storeVersion == eventStoreVersion.storeVersion) {
          buildFromState
        }
        else {
          val newState = rebuildState(buildFromState)
          updateStoredCache(newState)
          newState
        }
      }

      def rebuildState(buildFromState: AtomicEventStoreStateSql[D, P]): AtomicEventStoreStateSql[D, P] = {
        val range = EventStoreRange(buildFromState.version.add(1), Some(eventStoreVersion))
        logger.debug(s"Building event store state for range ${range}")
        val events: Seq[D#Event] = rangeExtractor(range)
        val updatedState = (buildFromState /: events) {
          case (state, event) => {
            val updatedVersion = state.version.add(1)
            val updatedProjection = stateCreationLogic.projectSingleEvent(state.projection, event)
            AtomicEventStoreStateSql(updatedVersion, updatedProjection)
          }
        }
        updatedState
      }
    })
    cachedState.projection
  }

}

case class AtomicEventStoreStateSql[D <: DomainSpecification, P <: D#State](version: EventStoreVersion, projection: P)

class EventStoreSqlReadOnlyReadTransaction[D <: DomainSpecification, P <: D#State](
    val db:         DB,
    val stateCache: EventStoreAtomicProjectionCache[D, P],
    val schema:     EventSerializationSchema[D],
    val ddlDialect: EventStoreSqlDDL
) extends EventStoreReadTransaction[D, P] with VersionedSqlTransaction {

  override lazy val projectionState: P = stateCache.buildState(eventStoreVersion, extractEventRange)

  def extractEventRange(range: EventStoreRange): Seq[D#Event] = {
    db.withinTx { implicit session =>
      val from = range.from.storeVersion
      val listEvent: List[EventDataWithNr] = range.to match {
        case Some(toVersion) =>

          val to = toVersion.storeVersion
          sql"select * from ${ddlDialect.eventDataTableSql} where event_nr >= ${from} and event_nr <= ${to} order by event_nr".map(rs => SqlEventDataMapping.eventDataWithNr(rs)).list.apply()
        case _ =>
          if (range.from.storeVersion == 0L) {
            sql"select * from ${ddlDialect.eventDataTableSql} order by event_nr".map(rs => SqlEventDataMapping.eventDataWithNr(rs)).list.apply()
          }
          else {
            sql"select * from ${ddlDialect.eventDataTableSql} where event_nr >= ${from} order by event_nr".map(rs => SqlEventDataMapping.eventDataWithNr(rs)).list.apply()
          }
      }
      listEvent.map(schema.mapToEvent).toStream
    }
  }

  override def calculateAggregatesVersions(aggregates: Set[D#Aggregate]): Map[D#Aggregate, Long] = db.withinTx { implicit session =>
      def extractAggregateVersion(aggRef: AggregateReference): Long = {
        val aggregateType = aggRef.aggregateType
        val uniqueId = aggRef.aggregateUniqueId
        val version: Option[Long] = sql"select max(version) from ${ddlDialect.aggregatesTableSql} where aggregate_type = ${aggregateType} and unique_id=${uniqueId}".map(rs => rs.longOpt(1)).toOption().apply().flatten
        version.getOrElse(0L)
      }

    val aggregateToVersion: Map[D#Aggregate, Long] = aggregates.map { aggregate =>
      aggregate -> extractAggregateVersion(schema.buildReference(aggregate))
    }(collection.breakOut)

    aggregateToVersion
  }

  override def loadEventsForAggregate(aggregate: D#Aggregate): Seq[D#Event] = {
    val aggregateRef = schema.buildReference(aggregate)
    db.withinTx { implicit session =>
      val listEvent: List[EventDataWithNr] =
        sql"""select * from ${ddlDialect.eventDataTableSql}
             where aggregate_type = ${aggregateRef.aggregateType} and unique_id=${aggregateRef.aggregateUniqueId} order by event_nr"""
          .map(rs => SqlEventDataMapping.eventDataWithNr(rs)).list.apply()
      listEvent.map(schema.mapToEvent).toStream
    }
  }
}

object SqlEventDataMapping {

  def eventDataWithNr(rs: WrappedResultSet): EventDataWithNr = new EventDataWithNr(
    rs.long("event_nr"), rs.bytes("binary_data"), rs.jodaDateTime("created_at")
  )
}
