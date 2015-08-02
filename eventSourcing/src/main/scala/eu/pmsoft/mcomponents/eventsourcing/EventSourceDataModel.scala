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

import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel._

import scala.concurrent.Future
import scala.language.implicitConversions
import scalaz.\/

object EventSourceDataModel {

  //Request/Response types
  type CommandResultOnDomain = ResponseError \/ EventSourceCommandConfirmation

  type RequestResult[R] = ResponseError \/ R

  type CommandVersionResult = ResponseError \/ EventStoreVersion


  //Commands/Events types

  type CommandToAggregateResult[A] = EventSourceCommandFailure \/ Set[A]

  type CommandResult = EventSourceCommandFailure \/ EventSourceCommandConfirmation

  type CommandToEventsResult[E] = EventSourceCommandFailure \/ List[E]

  type CommandPartialValidation[E] = EventSourceCommandFailure \/ E

  type CommandResultConfirmed = EventSourceCommandFailed \/ EventSourceCommandConfirmation


  implicit def commandToResponse(cmdResult: CommandResultConfirmed)(implicit serviceDomain: RequestErrorDomain):
  CommandResultToResponseResultTranslator = new DomainScopeCommandResultToResponseResultTranslator(cmdResult)

  implicit def errorToResponse(cmdError: EventSourceModelError)(implicit serviceDomain: RequestErrorDomain):
  EventErrorToResponseErrorTranslator = new DomainScopeEventSourceModelErrorToResponseResultTranslator(cmdError)

}

//Request/Response model
abstract class RequestHandler[Req, Res] {

  def handle(request: Req): Future[RequestResult[Res]]

}

case class RequestErrorCode(val code: Long) extends AnyVal

case class RequestErrorDomain(val domain: String) extends AnyVal

case class ResponseError(errorCode: RequestErrorCode, domain: RequestErrorDomain)


//Commands/Events model
/**
 * AnyVal type to define error codes after command execution
 * @param errorCode a numeric value that uniquely define the internal error
 */
case class EventSourceCommandError(val errorCode: Long) extends AnyVal

/**
 * Define the possible failure after a command execution
 */
sealed trait EventSourceCommandFailure

/**
 * A command failure that indicates that the command can not be executed
 * and client must notified about unsuccessful execution of command.
 * @param error code that inform about the internal failure reason.
 */
case class EventSourceCommandFailed(error: EventSourceCommandError) extends EventSourceCommandFailure

/**
 * Command execution has produced a transaction rollback because of concurrent command execution.
 * Command can be re-executed in the new event store state without client notification.
 */
case class EventSourceCommandRollback() extends EventSourceCommandFailure


case class EventStoreVersion(val storeVersion: Long) extends AnyVal

case class EventSourceCommandConfirmation(storeVersion: EventStoreVersion)

case class EventSourceModelError(description: String, code: EventSourceCommandError)


trait CommandResultToResponseResultTranslator {

  def asResponse: RequestResult[EventSourceCommandConfirmation]

}

class DomainScopeCommandResultToResponseResultTranslator(val cmdResult: CommandResultConfirmed)
                                                        (implicit val serviceDomain: RequestErrorDomain)
  extends CommandResultToResponseResultTranslator {

  override def asResponse: RequestResult[EventSourceCommandConfirmation] = cmdResult.leftMap { cmdError =>
    ResponseError(RequestErrorCode(cmdError.error.errorCode), serviceDomain)
  }
}


trait EventErrorToResponseErrorTranslator {

  def toResponseError: ResponseError

}

class DomainScopeEventSourceModelErrorToResponseResultTranslator(val cmdError: EventSourceModelError)
                                                                (implicit val serviceDomain: RequestErrorDomain)
  extends EventErrorToResponseErrorTranslator {
  override def toResponseError: ResponseError = ResponseError(RequestErrorCode(cmdError.code.errorCode), serviceDomain)
}
