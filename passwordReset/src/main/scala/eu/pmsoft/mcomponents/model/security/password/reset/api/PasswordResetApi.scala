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

package eu.pmsoft.mcomponents.model.security.password.reset.api

import eu.pmsoft.domain.model.{ SessionToken, UserID, UserPassword }
import eu.pmsoft.mcomponents.eventsourcing.{ ApiModuleProvided, ApiModule, DomainCommandApi, EventSourceCommandConfirmation }
import eu.pmsoft.mcomponents.minstance.ReqResDataModel._
import eu.pmsoft.mcomponents.minstance._
import eu.pmsoft.mcomponents.model.security.password.reset._

import scala.concurrent.{ ExecutionContext, Future }
import scalaz._
import scalaz.std.scalaFuture._

trait PasswordResetModule extends ApiModuleProvided[PasswordResetDomain] {

  lazy val passwordResetApi: PasswordResetApi = new PasswordResetApiDispatcher(cmdApi)

}

object PasswordResetApi {
  implicit val requestErrorDomain = RequestErrorDomain("PasswordResetDomain")
}

trait PasswordResetApi {

  def initializeFlow(req: InitializePasswordResetFlowRequest): Future[RequestResult[InitializePasswordResetFlowResponse]]

  def cancelFlow(req: CancelPasswordResetFlowRequest): Future[RequestResult[CancelPasswordResetFlowResponse]]

  def confirmFlow(req: ConfirmPasswordResetFlowRequest): Future[RequestResult[ConfirmPasswordResetFlowResponse]]
}

private class PasswordResetApiDispatcher(val cmdApi: DomainCommandApi[PasswordResetDomain])(implicit val executionContext: ExecutionContext) extends PasswordResetApi {

  import PasswordResetApi._

  override def initializeFlow(req: InitializePasswordResetFlowRequest): Future[RequestResult[InitializePasswordResetFlowResponse]] = (for {
    cmdConfirmation <- EitherT(cmdApi.commandHandler.execute(
      InitializePasswordResetFlow(req.userId, req.sessionToken)
    ).map(_.asResponse))
  } yield InitializePasswordResetFlowResponse(cmdConfirmation)).run

  override def cancelFlow(req: CancelPasswordResetFlowRequest): Future[RequestResult[CancelPasswordResetFlowResponse]] = (for {
    cmdConfirmation <- EitherT(cmdApi.commandHandler.execute(
      CancelPasswordResetFlowByToken(req.passwordResetToken)
    ).map(_.asResponse))
  } yield CancelPasswordResetFlowResponse(cmdConfirmation)).run

  override def confirmFlow(req: ConfirmPasswordResetFlowRequest): Future[RequestResult[ConfirmPasswordResetFlowResponse]] = (for {
    cmdConfirmation <- EitherT(cmdApi.commandHandler.execute(
      ConfirmPasswordResetFlow(req.sessionToken, req.passwordResetToken, req.newPassword)
    ).map(_.asResponse))
  } yield ConfirmPasswordResetFlowResponse(cmdConfirmation)).run
}

case class InitializePasswordResetFlowRequest(
  userId:       UserID,
  sessionToken: SessionToken
)

// reset password should be send by a email from a projection
case class InitializePasswordResetFlowResponse(confirmation: EventSourceCommandConfirmation[PasswordResetAggregate])

case class CancelPasswordResetFlowRequest(passwordResetToken: PasswordResetToken)

case class CancelPasswordResetFlowResponse(confirmation: EventSourceCommandConfirmation[PasswordResetAggregate])

case class ConfirmPasswordResetFlowRequest(
  sessionToken:       SessionToken,
  passwordResetToken: PasswordResetToken,
  newPassword:        UserPassword
)

case class ConfirmPasswordResetFlowResponse(confirmation: EventSourceCommandConfirmation[PasswordResetAggregate])

