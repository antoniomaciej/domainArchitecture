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

package eu.pmsoft.mcomponents.model.user.registry.mins

import eu.pmsoft.domain.model.{UserPassword, UserLogin, UserID}
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.minstance.ApiVersion
import eu.pmsoft.mcomponents.model.user.registry._
import eu.pmsoft.mcomponents.reqres.ReqResDataModel._
import eu.pmsoft.mcomponents.reqres.{ReqResDataModel, RequestErrorDomain}

import scala.concurrent.Future


object UserRegistrationApi {
  val version = ApiVersion(0, 0, 1)
}

object UserRegistrationApplicationDefinitions {
  implicit val requestErrorDomain = RequestErrorDomain("UserRegistration")
}

trait UserRegistrationApi {

  def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]]

  def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]]

}


object UserRegistrationRequestModel {

  val userIdNotFoundErrorCode = 5001L
  val userIdNotFound = EventSourceModelError("UserId not found. Login and password do not match any user.",
    EventSourceCommandError(userIdNotFoundErrorCode))

  val criticalUserNotFoundAfterSuccessRegistrationErrorCode = 5101L
  val criticalUserNotFoundAfterSuccessRegistration = EventSourceModelError("UserId not found after a success registration command.",
    EventSourceCommandError(criticalUserNotFoundAfterSuccessRegistrationErrorCode))
}


case class SearchForUserIdRequest(login: UserLogin, passwordHash: UserPassword)

case class SearchForUserIdResponse(userId: UserID)

case class RegisterUserRequest(login: UserLogin, passwordHash: UserPassword)

case class RegisterUserResponse(userID: UserID)
