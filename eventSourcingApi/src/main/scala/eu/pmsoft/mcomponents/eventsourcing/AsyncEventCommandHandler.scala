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

import scala.concurrent.{ExecutionContext, Future}

trait DomainSpecification {
  type Command
  type Event
  type Aggregate
  type State
}

trait DomainLogic[D <: DomainSpecification] {
  def executeCommand(command: D#Command, transactionScope: Map[D#Aggregate, Long])(implicit state: D#State): CommandToEventsResult[D#Event]
}

trait CommandToTransactionScope[D <: DomainSpecification] {
  def calculateTransactionScope(command: D#Command, state: D#State): CommandToAggregateResult[D#Aggregate]
}

trait AsyncEventHandlingModule[D <: DomainSpecification] {

  implicit def executionContext: ExecutionContext

  def commandHandler: AsyncEventCommandHandler[D]

  def state: AtomicEventStoreView[D#State]

}

trait AsyncEventCommandHandler[D <: DomainSpecification] {

  def execute(command: D#Command): Future[CommandResultConfirmed]

}

trait DomainLogicSpecification[D <: DomainSpecification] {

  def logic: DomainLogic[D]

  def atomicProjection: VersionedEventStoreView[D#Aggregate, D#State]

  def storeStorage: AsyncEventStore[D#Event, D#Aggregate]

  def transactionScopeCalculator: CommandToTransactionScope[D]

  implicit def executionContext: ExecutionContext

}
