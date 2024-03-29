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

package eu.pmsoft.mcomponents.eventsourcing

import scalaz.\/

//Event store commands execution model

object EventSourceCommandEventModel {

  //Commands/Events types

  type CommandToAggregateScope[D <: DomainSpecification] = EventSourceCommandFailure \/ Set[D#Aggregate]
  type CommandToConstraints[D <: DomainSpecification] = EventSourceCommandFailure \/ Set[D#ConstraintScope]
  type CommandToAtomicState[D <: DomainSpecification] = EventSourceCommandFailure \/ AtomicTransactionScope[D]

  type CommandResult[D <: DomainSpecification] = EventSourceCommandFailure \/ EventSourceCommandConfirmation[D#Aggregate]

  type CommandToEventsResult[D <: DomainSpecification] = EventSourceCommandFailure \/ CommandModelResult[_ <: D#Event, _ <: D#Aggregate]

  type CommandPartialValidation[E] = EventSourceCommandFailure \/ E

  type CommandResultConfirmed[A] = EventSourceCommandFailed \/ EventSourceCommandConfirmation[A]

}

case class CommandModelResult[E, A](events: List[E], rootAggregate: A)

//Commands/Events model
/** AnyVal type to define error codes after command execution
 *  @param errorCode a numeric value that uniquely define the internal error
 */
case class EventSourceCommandError(val errorCode: Long) extends AnyVal

/** Define the possible failure after a command execution
 */
sealed trait EventSourceCommandFailure

/** A command failure that indicates that the command can not be executed
 *  and client must notified about unsuccessful execution of command.
 *  @param error code that inform about the internal failure reason.
 */
case class EventSourceCommandFailed(error: EventSourceCommandError) extends EventSourceCommandFailure

/** Command execution has produced a transaction rollback because of concurrent command execution.
 *  Command can be re-executed in the new event store state without client notification.
 */
case class EventSourceCommandRollback() extends EventSourceCommandFailure

case class EventStoreVersion(val storeVersion: Long) extends AnyVal {
  def add(delta: Long): EventStoreVersion = EventStoreVersion(storeVersion + delta)
}

object EventStoreVersion {
  val zero = EventStoreVersion(0L)
}

/** A event range [from,to). If to is None, then the range is [from,infinity).
 *  @param from inclusive start event number
 *  @param to exclusive end event number
 */
case class EventStoreRange(from: EventStoreVersion, to: Option[EventStoreVersion]) {
  assert(from.storeVersion >= 0)
  assert(from.storeVersion <= to.getOrElse(from).storeVersion)
}

case class EventSourceCommandConfirmation[A](storeVersion: EventStoreVersion, rootAggregate: A)

case class EventSourceModelError(description: String, code: EventSourceCommandError)
