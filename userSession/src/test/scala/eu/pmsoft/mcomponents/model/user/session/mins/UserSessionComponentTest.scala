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

import eu.pmsoft.domain.model.{UserPassword, UserLogin, ComponentSpec}
import eu.pmsoft.domain.model.user.registry.mins._
import eu.pmsoft.mcomponents.minstance.{MicroComponentRegistry, ApiContract}
import eu.pmsoft.mcomponents.model.session.mins.{UserLoginRequest, UserSessionApi, UserSessionComponent}
import eu.pmsoft.mcomponents.model.user.registry.inmemory.UserRegistrationInMemoryApplication
import eu.pmsoft.mcomponents.model.user.registry.UserRegistrationApplication
import eu.pmsoft.mcomponents.model.user.session.UserSessionApplication
import eu.pmsoft.mcomponents.model.user.session.inmemory.UserSessionInMemoryApplication

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class UserSessionComponentTest extends ComponentSpec {

  it should "create sessions for a registered user" in {

    val registry = componentsInitialization()

    val userSessionApi = registry.bindComponent(
      ApiContract(classOf[UserSessionApi])).futureValue

    val userRegistrationApi = registry.bindComponent(
      ApiContract(classOf[UserRegistrationApi])).futureValue

    userSessionApi.loginUser(UserLoginRequest(
      UserLogin("testLogin@valid.com"), UserPassword("testPassword")
    )).futureValue shouldBe -\/

    userRegistrationApi.registerUser(RegisterUserRequest(
      UserLogin("testLogin@valid.com"), UserPassword("testPassword")
    )).futureValue shouldBe \/-

    userSessionApi.loginUser(UserLoginRequest(
      UserLogin("testLogin@valid.com"), UserPassword("testPassword")
    )).futureValue shouldBe \/-

  }

  def componentsInitialization(): MicroComponentRegistry = {
    val registry = MicroComponentRegistry.create()

    val userSession = new UserSessionComponent {
      override lazy val userRegistrationService: Future[UserRegistrationApi] =
        registry.bindComponent(ApiContract(classOf[UserRegistrationApi]))

      override lazy val applicationModule: UserSessionApplication = new UserSessionInMemoryApplication()
    }

    val userRegistration = new UserRegistrationComponent {

      override lazy val applicationModule: UserRegistrationApplication = new UserRegistrationInMemoryApplication()
    }

    registry.registerComponent(userSession)
    registry.registerComponent(userRegistration)

    registry.initializeInstances().futureValue shouldBe \/-
    registry
  }

}
