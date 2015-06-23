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

import com.softwaremill.macwire._
import eu.pmsoft.domain.minstance._
import eu.pmsoft.domain.model.EventSourceDataModel._
import eu.pmsoft.domain.model.OrderedEventStoreProjector
import eu.pmsoft.domain.model.user.registry.mins.UserRegistrationApi
import eu.pmsoft.domain.model.user.session.{UserSessionApplication, UserSessionSSOState}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait UserSessionComponent extends MicroComponent[UserSessionApi] {
  override def providedContact: MicroComponentContract[UserSessionApi] =
    MicroComponentModel.contractFor(UserSessionApi.version, classOf[UserSessionApi])

  def userRegistrationService: Future[UserRegistrationApi]

  def applicationModule: UserSessionApplication

  override lazy val app: Future[UserSessionApi] = for {
    userRegistrationRef <- userRegistrationService
  } yield new UserSessionInternalInjector {
      override lazy val userRegistration: UserRegistrationApi = userRegistrationRef

      override lazy val module: UserSessionApplication = applicationModule
    }.app

}

trait UserSessionInternalInjector {
  def userRegistration: UserRegistrationApi

  def module: UserSessionApplication

  lazy val commandHandler = module.commandHandler
  lazy val projection: OrderedEventStoreProjector[UserSessionSSOState] = module.applicationContextProvider.contextStateAtomicProjection
  lazy val loginHandler = wire[LoginRequestHandler]
  lazy val app = wire[UserServiceComponentInstance]

}

trait UserSessionApi {

  def loginUser(loginRequest: UserLoginRequest): Future[RequestResult[UserLoginResponse]]

}

object UserSessionApi {
  val version = ApiVersion(0, 0, 1)
}

class UserServiceComponentInstance(val loginRequestHandler: LoginRequestHandler) extends UserSessionApi {
  override def loginUser(loginRequest: UserLoginRequest): Future[RequestResult[UserLoginResponse]] =
    loginRequestHandler.handle(loginRequest)
}
