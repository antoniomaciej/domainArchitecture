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

package eu.pmsoft.mcomponents.model.security.password.reset.mins

import eu.pmsoft.domain.model.{SessionToken, UserID, UserPassword}
import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandConfirmation
import eu.pmsoft.mcomponents.minstance.ApiVersion
import eu.pmsoft.mcomponents.model.security.password.reset.PasswordResetToken
import eu.pmsoft.mcomponents.reqres.ReqResDataModel.RequestResult
import eu.pmsoft.mcomponents.reqres.RequestErrorDomain

import scala.concurrent.Future

object PasswordResetApi {
  val version = ApiVersion(0, 0, 1)
}

object PasswordResetApiDefinitions {
  implicit val requestErrorDomain = RequestErrorDomain("PasswordResetDomain")
}

object PasswordResetApiModel {

}

trait PasswordResetApi {

  def initializeFlow(req: InitializePasswordResetFlowRequest): Future[RequestResult[InitializePasswordResetFlowResponse]]

  def cancelFlow(req: CancelPasswordResetFlowRequest): Future[RequestResult[CancelPasswordResetFlowResponse]]

  def confirmFlow(req: ConfirmPasswordResetFlowRequest): Future[RequestResult[ConfirmPasswordResetFlowResponse]]
}

case class InitializePasswordResetFlowRequest(userId: UserID,
                                              sessionToken: SessionToken)

// reset password should be send by a email from a projection
case class InitializePasswordResetFlowResponse(confirmation: EventSourceCommandConfirmation)

case class CancelPasswordResetFlowRequest(passwordResetToken: PasswordResetToken)

case class CancelPasswordResetFlowResponse(confirmation: EventSourceCommandConfirmation)

case class ConfirmPasswordResetFlowRequest(sessionToken: SessionToken,
                                           passwordResetToken: PasswordResetToken,
                                           newPassword: UserPassword)

case class ConfirmPasswordResetFlowResponse(confirmation: EventSourceCommandConfirmation)

