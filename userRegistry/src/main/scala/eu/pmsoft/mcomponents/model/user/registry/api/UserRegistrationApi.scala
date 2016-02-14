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

package eu.pmsoft.mcomponents.model.user.registry.api

import eu.pmsoft.domain.model.{ UserID, UserLogin, UserPassword }
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.minstance.ReqResDataModel._
import eu.pmsoft.mcomponents.minstance._
import eu.pmsoft.mcomponents.model.user.registry.{ AddUser, UserRegistrationDomain }

import scala.concurrent.{ ExecutionContext, Future }
import scalaz._
import scalaz.std.scalaFuture._

trait UserRegistrationModule extends ApiModuleProvided[UserRegistrationDomain] {

  lazy val userRegistrationApi: UserRegistrationApi = new UserRegistrationRequestDispatcher(cmdApi)

}

object UserRegistrationApi {
  implicit val requestErrorDomain = RequestErrorDomain("UserRegistration")
}

trait UserRegistrationApi {

  def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]]

  def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]]

}

class UserRegistrationRequestDispatcher(val commandApi: DomainCommandApi[UserRegistrationDomain])(implicit val executionContext: ExecutionContext)
    extends UserRegistrationApi {

  import UserRegistrationApi._

  override def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]] = {
    val state = commandApi.atomicProjection.lastSnapshot()
    val result = state.getUserByLogin(searchForUser.login).filter(_.passwordHash == searchForUser.passwordHash) match {
      case Some(user) => \/-(SearchForUserIdResponse(user.uid))
      case None       => -\/(UserRegistrationRequestModel.userIdNotFound.toResponseError)
    }
    Future.successful(result)
  }

  override def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]] =
    (for {
      cmdResult <- EitherT(commandApi.commandHandler.execute(AddUser(registrationRequest.login, registrationRequest.passwordHash)).map(_.asResponse))
      response <- EitherT(findCreatedUser(cmdResult, registrationRequest.login))
    } yield response).run

  private def findCreatedUser(cmdResult: EventSourceCommandConfirmation[UserRegistrationDomain#Aggregate], login: UserLogin): Future[RequestResult[RegisterUserResponse]] =
    commandApi.atomicProjection.atLeastOn(cmdResult.storeVersion).map { state =>
      state.projection.getUserByLogin(login) match {
        case Some(user) => \/-(RegisterUserResponse(user.uid))
        case None       => -\/(UserRegistrationRequestModel.criticalUserNotFoundAfterSuccessRegistration.toResponseError)
      }
    }

}

object UserRegistrationRequestModel {

  val userIdNotFoundErrorCode = 5001L
  val userIdNotFound = EventSourceModelError(
    "UserId not found. Login and password do not match any user.",
    EventSourceCommandError(userIdNotFoundErrorCode)
  )

  val criticalUserNotFoundAfterSuccessRegistrationErrorCode = 5101L
  val criticalUserNotFoundAfterSuccessRegistration = EventSourceModelError(
    "UserId not found after a success registration command.",
    EventSourceCommandError(criticalUserNotFoundAfterSuccessRegistrationErrorCode)
  )
}

case class SearchForUserIdRequest(login: UserLogin, passwordHash: UserPassword)

case class SearchForUserIdResponse(userId: UserID)

case class RegisterUserRequest(login: UserLogin, passwordHash: UserPassword)

case class RegisterUserResponse(userID: UserID)
