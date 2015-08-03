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

import eu.pmsoft.domain.model.{UserPassword, UserLogin, ComponentSpec}
import eu.pmsoft.mcomponents.minstance.{MicroComponentRegistry, ApiContract}
import eu.pmsoft.mcomponents.model.user.registry._
import eu.pmsoft.mcomponents.model.user.registry.inmemory.UserRegistrationInMemoryApplication

class UserRegistrationComponentTest extends ComponentSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  it should "fail to register a nor valid email login" in {

    val registry = componentsInitialization()

    val userRegistrationApi = registry.bindComponent(
      ApiContract(classOf[UserRegistrationApi])).futureValue

    userRegistrationApi.registerUser(RegisterUserRequest(
      UserLogin("testLogin@invalid"), UserPassword("testPassword")
    )).futureValue shouldBe -\/

  }

  def componentsInitialization(): MicroComponentRegistry = {
    val registry = MicroComponentRegistry.create()

    val userRegistration = new UserRegistrationComponent {

      override lazy val applicationModule: UserRegistrationApplication = new UserRegistrationInMemoryApplication()
    }

    registry.registerComponent(userRegistration)
    registry.initializeInstances().futureValue shouldBe \/-
    registry
  }
}
