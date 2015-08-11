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


trait DomainLogic[C, E, A, S] {
  def executeCommand(command: C, transactionScope: Map[A, Long])(implicit state: S): CommandToEventsResult[E]
}

trait CommandToTransactionScope[C, A, S] {
  def calculateTransactionScope(command: C, state: S): CommandToAggregateResult[A]
}

trait AsyncEventHandlingModule[C, E, S] {

  implicit def executionContext: ExecutionContext

  def commandHandler: AsyncEventCommandHandler[C]

  def state: AtomicEventStoreView[S]

}

trait AsyncEventCommandHandler[C] {

  def execute(command: C): Future[CommandResultConfirmed]

}

trait DomainLogicSpecification[C, E, A, S] {

  def logic: DomainLogic[C, E, A, S]

  def atomicProjection: VersionedEventStoreView[A, S]

  def storeStorage: AsyncEventStore[E, A]

  def transactionScopeCalculator: CommandToTransactionScope[C, A, S]

  implicit def executionContext: ExecutionContext

}
