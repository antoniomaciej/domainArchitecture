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
 */

package eu.pmsoft.domain.model

import eu.pmsoft.domain.model.EventSourceEngine._

import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.std.scalaFuture._


object EventSourceEngine {

  type CommandResult = EventSourceCommandError \/ EventSourceCommandConfirmation

  type CommandToEventsResult[E] = EventSourceCommandError \/ List[E]

  type CommandPartialValidation[E] = EventSourceCommandError \/ E

}

case class EventStoreVersion(val storeVersion: Long) extends AnyVal

case class EventSourceCommandError(val errorCode: Long) extends AnyVal

case class EventSourceCommandConfirmation(storeVersion: EventStoreVersion)

case class EventSourceModelError(description:String, code: EventSourceCommandError)

trait DomainLogic[C, E, S] {
  def executeCommand(command: C)(implicit state: S): CommandToEventsResult[E]
}


trait AtomicEventStoreProjection[+P] {

  def projection(): P

}

trait OrderedEventStoreProjector[+P] {

  def atLeastOn(storeVersion: EventStoreVersion): P
}


//Async domain handler
trait AsyncEventStore[E] {

  def persistEvents(events: List[E]): Future[CommandResult]

}

trait AsyncEventHandlingModule[C, E, S] {

  implicit def executionContext: ExecutionContext

  def commandHandler: AsyncEventCommandHandler[C]

  def state: AtomicEventStoreProjection[S]

}

trait AsyncEventCommandHandler[C] {

  def execute(command: C): Future[CommandResult]

}

trait DomainLogicAsyncEventCommandHandler[C, E, S] extends AsyncEventCommandHandler[C] {

  protected def logic: DomainLogic[C, E, S]

  protected def store: AsyncEventStore[E]

  protected def atomicProjection: AtomicEventStoreProjection[S]

  implicit def executionContext: ExecutionContext

  private def applyCommand(command: C) = Future.successful(logic.executeCommand(command)(atomicProjection.projection()))

  final def execute(command: C): Future[CommandResult] = (for {
    events <- EitherT(applyCommand(command))
    confirmation <- EitherT(store.persistEvents(events))
  } yield confirmation).run
}
