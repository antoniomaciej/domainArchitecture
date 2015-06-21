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

package eu.pmsoft.domain.model.user.session

import eu.pmsoft.domain.model.EventSourceEngine.CommandResult
import eu.pmsoft.domain.model.ReqResApiEngine._
import eu.pmsoft.domain.model._
import eu.pmsoft.domain.model.security.password.reset.SessionToken
import eu.pmsoft.domain.model.userRegistry.{UserID, UserLogin, UserPassword, UserRegistrationApi}

import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.std.scalaFuture._

object EventSourceRequestModel {


}

trait UserSessionApi {

}

trait UserSessionService extends UserServiceDependencies {

  implicit def executionContext: ExecutionContext

  def commandHandler: AsyncEventCommandHandler[UserSessionCommand]

  def userSessionProjection: OrderedEventStoreProjector[UserSessionApi]

  def loginUser(loginReq: UserLoginRequest): Future[RequestResult[UserLoginResponse]] = (for {
    userId <- EitherT(findUserID(loginReq.login, loginReq.passwordHash))
    cmdResult <- EitherT(commandHandler.execute(CreateUserSession(userId)).map(translateCmdResultToReq))
    userSession <- EitherT(findUserSession(cmdResult))
    res <- EitherT(createResponseFromSession(userSession))
  } yield res).run

  def translateCmdResultToReq(cmdResult:CommandResult) :
  RequestResult[EventSourceCommandConfirmation] =
    cmdResult.leftMap{ cmdError =>
      ResponseError(RequestErrorCode(cmdError.errorCode),RequestErrorDomain("UserSession"))
    }

  def findUserID(login: UserLogin, passwordHash: UserPassword):
  Future[RequestResult[UserID]] = ???

  def findUserSession(cmdResult: EventSourceCommandConfirmation):
  Future[RequestResult[UserSession]] = ???

  def createResponseFromSession(userSession: UserSession):
  Future[RequestResult[UserLoginResponse]] = ???



}

case class UserLoginRequest(login: UserLogin, passwordHash: UserPassword)

case class UserLoginResponse(sessionToken: SessionToken)

trait UserServiceDependencies {

  def userRegistration: UserRegistrationApi

}
