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

package eu.pmsoft.mcomponents.model.user.session.mins

import eu.pmsoft.domain.model.{ UserID, UserSession }
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.minstance.{ MicroComponent, MicroComponentContract, MicroComponentModel }
import eu.pmsoft.mcomponents.model.user.registry.mins.{ SearchForUserIdRequest, UserRegistrationApi }
import eu.pmsoft.mcomponents.model.user.session._

import scala.concurrent.{ ExecutionContext, Future }
import scalaz._
import scalaz.std.scalaFuture._

trait UserSessionComponent extends MicroComponent[UserSessionApi] {
  override def providedContact: MicroComponentContract[UserSessionApi] =
    MicroComponentModel.contractFor(UserSessionApi.version, classOf[UserSessionApi])

  def userRegistrationService: Future[UserRegistrationApi]

  def application: DomainCommandApi[UserSessionSSODomain]

  override lazy val app: Future[UserSessionApi] = for {
    userRegistrationRef <- userRegistrationService
  } yield new UserServiceComponentInstance(userRegistrationRef, application)

}

class UserServiceComponentInstance(
  val userRegistration:  UserRegistrationApi,
  val sessionCommandApi: DomainCommandApi[UserSessionSSODomain]
)(implicit val executionContext: ExecutionContext)
    extends UserSessionApi {

  import UserSessionApplicationDefinitions._
  import eu.pmsoft.mcomponents.minstance.ReqResDataModel._

  override def loginUser(loginRequest: UserLoginRequest): Future[RequestResult[UserLoginResponse]] = (for {
    userIdRes <- EitherT(userRegistration.findRegisteredUser(SearchForUserIdRequest(loginRequest.login, loginRequest.passwordHash)))
    cmdResult <- EitherT(sessionCommandApi.commandHandler.execute(CreateUserSession(userIdRes.userId)).map(_.asResponse))
    userSession <- EitherT(findUserSession(cmdResult, userIdRes.userId))
    res <- EitherT(createResponseFromSession(userSession))
  } yield res).run

  def findUserSession(cmdResult: EventSourceCommandConfirmation, userId: UserID): Future[RequestResult[UserSession]] = sessionCommandApi.atomicProjection.atLeastOn(cmdResult.storeVersion).map { state =>
    state.findUserSession(userId) match {
      case Some(session) => \/-(session)
      case None          => -\/(UserSessionModel.criticalSessionNotFoundAfterSuccessCommand.toResponseError)
    }
  }

  def createResponseFromSession(userSession: UserSession): Future[RequestResult[UserLoginResponse]] = Future.successful(\/-(UserLoginResponse(userSession.sessionToken)))

}
