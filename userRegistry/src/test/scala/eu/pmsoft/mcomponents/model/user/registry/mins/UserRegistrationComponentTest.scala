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

import eu.pmsoft.domain.model.{UserLogin, UserPassword}
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing.{EventSourceExecutionContextProvider, EventSourcingConfiguration}
import eu.pmsoft.mcomponents.minstance.{ApiContract, MicroComponentRegistry}
import eu.pmsoft.mcomponents.model.user.registry._
import eu.pmsoft.mcomponents.model.user.registry.inmemory.UserRegistrationInMemoryInfrastructure
import eu.pmsoft.mcomponents.test.BaseEventSourceSpec

class UserRegistrationComponentTest extends BaseEventSourceSpec {


  it should "fail to register a invalid email login" in {
    //given
    val registry = componentsInitialization()
    val userRegistrationApi = registry.bindComponent(
      ApiContract(classOf[UserRegistrationApi])).futureValue
    //when then
    userRegistrationApi.registerUser(RegisterUserRequest(
      UserLogin("testLogin@invalid"), UserPassword("testPassword")
    )).futureValue shouldBe -\/
  }

  it should "register a valid email login" in {
    //given
    val registry = componentsInitialization()
    val userRegistrationApi = registry.bindComponent(
      ApiContract(classOf[UserRegistrationApi])).futureValue

    //when used not registered
    //then find fail
    userRegistrationApi.findRegisteredUser(SearchForUserIdRequest(
      UserLogin("testLogin@domain.com"),
      UserPassword("testPassword"))
    ).futureValue shouldBe -\/

    //when used registered
    userRegistrationApi.registerUser(RegisterUserRequest(
      UserLogin("testLogin@domain.com"), UserPassword("testPassword")
    )).futureValue shouldBe \/-
    //then find works
    userRegistrationApi.findRegisteredUser(SearchForUserIdRequest(
      UserLogin("testLogin@domain.com"),
      UserPassword("testPassword"))
    ).futureValue shouldBe \/-

  }

  def componentsInitialization(): MicroComponentRegistry = {

    import scala.concurrent.ExecutionContext.Implicits.global
    val registry = MicroComponentRegistry.create()
    val configuration: EventSourcingConfiguration = EventSourcingConfiguration(global,LocalBindingInfrastructure.create())
    implicit val eventSourceExecutionContext = EventSourceExecutionContextProvider.create()(configuration)
    val applicationInstance = UserRegistrationApplication.createApplication(UserRegistrationInMemoryInfrastructure.createInfrastructure())

    val userRegistration = new UserRegistrationComponent {

      override lazy val application: UserRegistrationApplication = applicationInstance

    }

    registry.registerComponent(userRegistration)
    registry.initializeInstances().futureValue shouldBe \/-
    registry
  }
}
