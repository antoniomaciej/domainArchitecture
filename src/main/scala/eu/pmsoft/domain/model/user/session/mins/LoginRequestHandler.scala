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

package eu.pmsoft.domain.model.user.session.mins

import eu.pmsoft.domain.model.EventSourceDataModel._
import eu.pmsoft.domain.model.user.registry.UserID
import eu.pmsoft.domain.model.user.registry.mins.{SearchForUserIdRequest, UserRegistrationApi}
import eu.pmsoft.domain.model.user.session._
import eu.pmsoft.domain.model.user.session.mins.UserSessionApplicationDefinitions._
import eu.pmsoft.domain.model.{AsyncEventCommandHandler, EventSourceCommandConfirmation, OrderedEventStoreProjector, RequestHandler}

import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.std.scalaFuture._

class LoginRequestHandler(val userRegistration: UserRegistrationApi,
                          val commandHandler: AsyncEventCommandHandler[UserSessionCommand],
                          val userSessionProjection: OrderedEventStoreProjector[UserSessionSSOState])
                         (implicit val executionContext: ExecutionContext)
  extends RequestHandler[UserLoginRequest, UserLoginResponse] {

  override def handle(request: UserLoginRequest): Future[RequestResult[UserLoginResponse]] = (for {
    userIdRes <- EitherT(userRegistration.findRegisteredUser(SearchForUserIdRequest(request.login, request.passwordHash)))
    cmdResult <- EitherT(commandHandler.execute(CreateUserSession(userIdRes.userId)).map(_.asResponse))
    userSession <- EitherT(findUserSession(cmdResult, userIdRes.userId))
    res <- EitherT(createResponseFromSession(userSession))
  } yield res).run

  def findUserSession(cmdResult: EventSourceCommandConfirmation, userId: UserID):
  Future[RequestResult[UserSession]] = userSessionProjection
    .atLeastOn(cmdResult.storeVersion).map { state =>
    state.findUserSession(userId) match {
      case Some(session) => \/-(session)
      case None => -\/(UserSessionModel.criticalSessionNotFoundAfterSuccessCommand.toResponseError)
    }
  }

  def createResponseFromSession(userSession: UserSession):
  Future[RequestResult[UserLoginResponse]] = Future.successful(\/-(UserLoginResponse(userSession.sessionToken)))

}
