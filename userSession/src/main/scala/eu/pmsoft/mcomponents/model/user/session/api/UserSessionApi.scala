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

package eu.pmsoft.mcomponents.model.user.session.api

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import eu.pmsoft.mcomponents.eventsourcing.{ ApiModuleProvided, ApiModule, EventSourceCommandConfirmation, DomainCommandApi }
import eu.pmsoft.mcomponents.minstance.ReqResDataModel._
import eu.pmsoft.mcomponents.minstance._
import eu.pmsoft.mcomponents.model.user.registry.UserRegistrationDomain
import eu.pmsoft.mcomponents.model.user.registry.api.{ SearchForUserIdRequest, UserRegistrationApi }
import eu.pmsoft.mcomponents.model.user.session.{ UserSessionSSOState, UserSessionModel, CreateUserSession, UserSessionSSODomain }

import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ -\/, \/-, EitherT }
import scalaz._
import scalaz.std.scalaFuture._

object UserSessionApi {

  implicit val requestErrorDomain = RequestErrorDomain("UserSession")
}

trait UserSessionApi {

  def loginUser(loginRequest: UserLoginRequest): Future[RequestResult[UserLoginResponse]]

}

trait UserSessionModule extends ApiModuleProvided[UserSessionSSODomain] {

  def userRegistrationApi: UserRegistrationApi

  lazy val userSessionApi: UserSessionApi = new UserServiceDispatcher(userRegistrationApi, cmdApi)

}

private class UserServiceDispatcher(
  val userRegistrationApi: UserRegistrationApi,
  val sessionCommandApi:   DomainCommandApi[UserSessionSSODomain]
)(implicit val executionContext: ExecutionContext)
    extends UserSessionApi {

  import UserSessionApi._
  import eu.pmsoft.mcomponents.minstance.ReqResDataModel._

  override def loginUser(loginRequest: UserLoginRequest): Future[RequestResult[UserLoginResponse]] = (for {
    userIdRes <- EitherT(userRegistrationApi.findRegisteredUser(SearchForUserIdRequest(loginRequest.login, loginRequest.passwordHash)))
    cmdResult <- EitherT(sessionCommandApi.commandHandler.execute(CreateUserSession(userIdRes.userId)).map(_.asResponse))
    userSession <- EitherT(findUserSession(cmdResult, userIdRes.userId))
    res <- EitherT(createResponseFromSession(userSession))
  } yield res).run

  def findUserSession(cmdResult: EventSourceCommandConfirmation[UserSessionSSODomain#Aggregate], userId: UserID): Future[RequestResult[UserSession]] = {
    val eventualProjection: Future[VersionedProjection[UserSessionSSOState]] = sessionCommandApi.atomicProjection.atLeastOn(cmdResult.storeVersion)
    eventualProjection.map { state =>
      state.projection.findUserSession(userId) match {
        case Some(session) => \/-(session)
        case None          => -\/(UserSessionModel.criticalSessionNotFoundAfterSuccessCommand.toResponseError)
      }
    }
  }

  def createResponseFromSession(userSession: UserSession): Future[RequestResult[UserLoginResponse]] = Future.successful(\/-(UserLoginResponse(userSession.sessionToken)))

}

case class UserLoginRequest(login: UserLogin, passwordHash: UserPassword)

case class UserLoginResponse(sessionToken: SessionToken)

