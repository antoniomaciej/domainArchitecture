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

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import rx.Observable

import scala.concurrent.Future
import scala.reflect._

trait EventStore[D <: DomainSpecification] extends EventStoreRead[D] {

  def calculateAtomicTransactionScopeVersion(logic: DomainLogic[D], command: D#Command): Future[CommandToAtomicState[D]]

  def persistEvents(events: List[D#Event], aggregateRoot: D#Aggregate, atomicTransactionScope: AtomicTransactionScope[D]): Future[CommandResult]

}
trait EventStoreRead[D <: DomainSpecification] {

  def loadEvents(range: EventStoreRange): Seq[D#Event]

  def loadEventsForAggregate(aggregate: D#Aggregate): Seq[D#Event]

  def eventStoreVersionUpdates(): Observable[EventStoreVersion]
}

case class EventStoreReference[D <: DomainSpecification](
  id:                EventStoreID,
  eventRootType:     ClassTag[D#Event],
  aggregateRootType: ClassTag[D#Aggregate]
)

case class EventStoreID(val id: String) extends AnyVal

trait EventStoreAtomicProjectionCreationLogic[D <: DomainSpecification, P <: D#State] {

  def buildInitialState(): P

  def projectSingleEvent(state: P, event: D#Event): P
}

