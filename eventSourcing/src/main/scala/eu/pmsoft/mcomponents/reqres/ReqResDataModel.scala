package eu.pmsoft.mcomponents.reqres

import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel.CommandResultConfirmed
import eu.pmsoft.mcomponents.eventsourcing.{EventSourceCommandConfirmation, EventSourceModelError, EventStoreVersion}

import scala.language.implicitConversions
import scalaz.\/

object ReqResDataModel {

  //Request/Response types
  type CommandResultOnDomain = ResponseError \/ EventSourceCommandConfirmation

  type RequestResult[R] = ResponseError \/ R

  type CommandVersionResult = ResponseError \/ EventStoreVersion

  implicit def commandToResponse(cmdResult: CommandResultConfirmed)(implicit serviceDomain: RequestErrorDomain):
  CommandResultToResponseResultTranslator = new DomainScopeCommandResultToResponseResultTranslator(cmdResult)

  implicit def errorToResponse(cmdError: EventSourceModelError)(implicit serviceDomain: RequestErrorDomain):
  EventErrorToResponseErrorTranslator = new DomainScopeEventSourceModelErrorToResponseResultTranslator(cmdError)

}

import eu.pmsoft.mcomponents.reqres.ReqResDataModel._

case class RequestErrorCode(val code: Long) extends AnyVal

case class RequestErrorDomain(val domain: String) extends AnyVal

case class ResponseError(errorCode: RequestErrorCode, domain: RequestErrorDomain)

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
