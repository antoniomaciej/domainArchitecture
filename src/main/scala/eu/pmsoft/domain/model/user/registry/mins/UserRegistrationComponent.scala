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

package eu.pmsoft.domain.model.user.registry.mins

import com.softwaremill.macwire._
import eu.pmsoft.domain.minstance.{ApiVersion, MicroComponent, MicroComponentContract, MicroComponentModel}
import eu.pmsoft.domain.model.EventSourceDataModel._
import eu.pmsoft.domain.model.user.registry.UserRegistrationApplication

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait UserRegistrationComponent extends MicroComponent[UserRegistrationApi] {
  override def providedContact: MicroComponentContract[UserRegistrationApi] =
    MicroComponentModel.contractFor(UserRegistrationApi.version, classOf[UserRegistrationApi])

  def applicationModule: UserRegistrationApplication

  override lazy val app: Future[UserRegistrationApi] = Future.successful(new UserRegistrationInternalInjector {
    override lazy val module: UserRegistrationApplication = applicationModule
  }.app)
}

trait UserRegistrationInternalInjector {
  def module: UserRegistrationApplication

  lazy val commandHandler = module.commandHandler

  lazy val projection = module.applicationContextProvider.contextStateAtomicProjection
  lazy val searchUserHandler = wire[SearchForUserIdHandler]
  lazy val userRegistrationHandler = wire[UserRegistrationHandler]
  lazy val app = wire[UserRegistrationApiForState]

}

object UserRegistrationApi {
  val version = ApiVersion(0, 0, 1)
}

trait UserRegistrationApi {

  def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]]

  def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]]

}


class UserRegistrationApiForState(val searchForUserIdHandler: SearchForUserIdHandler,
                                  val userRegistrationHandler: UserRegistrationHandler) extends UserRegistrationApi {

  override def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]] =
    searchForUserIdHandler.handle(searchForUser)

  override def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]] =
    userRegistrationHandler.handle(registrationRequest)
}
