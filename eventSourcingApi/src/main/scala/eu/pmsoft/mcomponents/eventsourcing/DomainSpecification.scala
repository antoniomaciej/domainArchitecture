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
 *
 */

package eu.pmsoft.mcomponents.eventsourcing

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing.eventstore._
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import org.joda.time.DateTime

import scala.concurrent.Future
import scalaz.{ \/-, \/ }

trait DomainSpecification {
  type Command
  type Event
  type Aggregate
  type ConstraintScope
  type State
  type SideEffects
}

trait DomainModule[D <: DomainSpecification] {

  def logic: DomainLogic[D]

  def sideEffects: D#SideEffects

  def schema: EventSerializationSchema[D]

  def eventStore: EventStore[D] with VersionedEventStoreView[D#State]

}

trait DomainLogic[D <: DomainSpecification] {
  def calculateRootAggregate(command: D#Command, state: D#State): CommandToAggregateScope[D]
  //TODO coverage
  def calculateConstraints(command: D#Command, state: D#State): CommandToConstraints[D] = \/-(Set())

  def executeCommand(command: D#Command, atomicTransactionScope: AtomicTransactionScope[D])(implicit state: D#State, sideEffects: D#SideEffects): CommandToEventsResult[D]
}

trait AsyncEventCommandHandler[D <: DomainSpecification] {

  def execute(command: D#Command): Future[CommandResultConfirmed[D#Aggregate]]

}

trait DomainCommandApi[D <: DomainSpecification] extends EventSourcingConfigurationContext {

  def commandHandler: AsyncEventCommandHandler[D]

  def atomicProjection: VersionedEventStoreView[D#State]

}

trait AtomicEventStoreView[+P] {

  def lastSnapshot(): P

}

trait VersionedEventStoreView[+P] extends AtomicEventStoreView[P] {

  def atLeastOn(storeVersion: EventStoreVersion): Future[VersionedProjection[P]]

}

case class AtomicTransactionScope[D <: DomainSpecification](aggregateVersion: Map[D#Aggregate, Long], constraintScopeVersion: Map[D#ConstraintScope, Long], projectionView: D#State)

trait EventSerializationSchema[D <: DomainSpecification] {
  def mapToEvent(data: EventDataWithNr): D#Event

  def eventToData(event: D#Event): EventData

  //TODO coverage for implementations
  def buildConstraintReference(constraintScope: D#ConstraintScope): ConstraintReference

  def buildAggregateReference(aggregate: D#Aggregate): AggregateReference
}

case class EventData(eventBytes: Array[Byte])

case class EventDataWithNr(eventNr: Long, eventBytes: Array[Byte], createdAt: DateTime)

case class AggregateReference(aggregateType: Int, aggregateUniqueId: String)
case class ConstraintReference(constraintType: Int, constraintUniqueId: String)

object AggregateReference {
  def apply(aggregateType: Int, aggregateUniqueId: Long): AggregateReference = {
    AggregateReference(aggregateType, "%d".format(aggregateUniqueId))
  }
}

object ConstraintReference {
  val noConstraintsOnDomain = ConstraintReference(-1, "noConstraintsOnDomain")

  def apply(constraintType: Int, aggregateUniqueId: Long): ConstraintReference = {
    ConstraintReference(constraintType, "%d".format(aggregateUniqueId))
  }
}

