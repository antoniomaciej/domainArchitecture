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

import eu.pmsoft.domain.model.{ UserLogin, UserPassword }
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.model.user.registry.api._
import eu.pmsoft.mcomponents.model.user.registry.{ UserRegistrationDomain, UserRegistrationDomainModule }
import eu.pmsoft.mcomponents.model.user.session.UserSessionSSODomain
import eu.pmsoft.mcomponents.model.user.session.inmemory.UserSessionDomainModule
import eu.pmsoft.mcomponents.test.BaseEventSourceComponentTestSpec
import scalikejdbc._
import scalikejdbc.config.DBs

import scala.concurrent.ExecutionContext.Implicits._

class UserSessionComponentTest extends BaseEventSourceComponentTestSpec {
  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(enabled = true, stackTraceDepth = 3)
  GlobalSettings.loggingSQLErrors = true
  DBs.setup('userRegistrationDB)

  it should "create sessions for a registered user" in {
    val module: UserSessionModule = buildModule()
    val userSessionApi: UserSessionApi = module.userSessionApi
    val userRegistrationApi: UserRegistrationApi = module.userRegistrationApi

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

  def buildModule(): UserSessionModule = {
    implicit val eventSourcingConfiguration = EventSourcingConfiguration(
      global,
      LocalBindingInfrastructure.create(),
      Set(
        EventStoreSqlBackend(UserRegistrationDomainModule.eventStoreReference, ConnectionPool('userRegistrationDB), H2EventStoreSqlDialect, "users", rebuildDDL = true),
        EventStoreInMemory(UserSessionDomainModule.eventStoreReference)
      )
    )
    implicit val eventSourceExecutionContext = EventSourceExecutionContextProvider.create()

    val userRegistrationCommandApi: DomainCommandApi[UserRegistrationDomain] = eventSourceExecutionContext.assemblyDomainApplication(new UserRegistrationDomainModule())

    val userRegistrationModule = new UserRegistrationModule {
      override lazy val cmdApi: DomainCommandApi[UserRegistrationDomain] = userRegistrationCommandApi
    }
    val sessionCommandApi: DomainCommandApi[UserSessionSSODomain] = eventSourceExecutionContext.assemblyDomainApplication(new UserSessionDomainModule())

    new UserSessionModule {
      override lazy val userRegistrationApi: UserRegistrationApi = userRegistrationModule.userRegistrationApi

      override lazy val cmdApi: DomainCommandApi[UserSessionSSODomain] = sessionCommandApi
    }
  }
}
