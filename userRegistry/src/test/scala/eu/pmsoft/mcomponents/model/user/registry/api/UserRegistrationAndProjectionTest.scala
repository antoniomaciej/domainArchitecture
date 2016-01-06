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
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.projection.{ EventSourceProjectionView, EventSourceProjectionCreationLogic, InMemoryProjections }
import eu.pmsoft.mcomponents.model.user.registry._
import eu.pmsoft.mcomponents.test.BaseEventSourceComponentTestSpec
import rx.observers.TestSubscriber
import rx.{ Observable, Subscriber }

class UserRegistrationAndProjectionTest extends BaseEventSourceComponentTestSpec {

  it should "Create stream of events" in {
    //given
    val config: EventSourcingConfiguration = configuration()
    val module: UserRegistrationModule = apiModule(config)
    val userRegistrationApi = module.userRegistrationApi
    //and a projection is bind
    val testSubscriber = new TestSubscriber[VersionedEvent[UserRegistrationDomain]]()
    val events: Observable[VersionedEvent[UserRegistrationDomain]] = config.bindingInfrastructure.consumerApi.eventStoreStream(UserRegistrationDomainModule.eventStoreReference, EventStoreVersion.zero)
    events.subscribe(testSubscriber)
    //when
    userRegistrationApi.registerUser(RegisterUserRequest(
      UserLogin("testLogin@test.com"), UserPassword("testPassword")
    )).futureValue shouldBe \/-
    //then

    import scala.collection.JavaConversions._
    testSubscriber.assertNoErrors()
    testSubscriber.assertNoTerminalEvent()
    testSubscriber.getOnNextEvents.toList should be(List(
      VersionedEvent[UserRegistrationDomain](EventStoreVersion(1L), UserCreated(UserID(0), UserLogin("testLogin@test.com"), UserPassword("testPassword")))
    ))
  }

  it should "Create inMemory projections" in {
    //given
    val config: EventSourcingConfiguration = configuration()
    val module: UserRegistrationModule = apiModule(config)
    val userRegistrationApi = module.userRegistrationApi
    //and a projection is bind
    val projection: EventSourceProjectionView[ExistingUsers] = InMemoryProjections.bindProjection(UserRegistrationDomainModule.eventStoreReference, new TestProjection(), config.bindingInfrastructure)
    //when
    userRegistrationApi.registerUser(RegisterUserRequest(
      UserLogin("testLogin@test.com"), UserPassword("testPassword")
    )).futureValue shouldBe \/-
    //then
    projection.lastSnapshotView().projection.userLoginSet should be(Set(UserLogin("testLogin@test.com")))
  }

  class TestProjection extends EventSourceProjectionCreationLogic[UserRegistrationDomain, ExistingUsers] {
    override def zero(): ExistingUsers = ExistingUsers()

    override def projectEvent(state: ExistingUsers, version: EventStoreVersion, event: UserRegistrationEvent): ExistingUsers =
      event match {
        case UserCreated(uid, login, passwordHash)     => state.copy(state.userLoginSet + login)
        case UserPasswordUpdated(userId, passwordHash) => state
        case UserActiveStatusUpdated(userId, active)   => state
        case UserObtainedAccessRoles(userId, roles)    => state
      }
  }

  case class ExistingUsers(userLoginSet: Set[UserLogin] = Set())

  def configuration(): EventSourcingConfiguration = {
    EventSourcingConfiguration(
      scala.concurrent.ExecutionContext.Implicits.global,
      LocalBindingInfrastructure.create(),
      Set(
        EventStoreInMemory(UserRegistrationDomainModule.eventStoreReference)
      )
    )
  }

  def apiModule(implicit eventSourcingConfiguration: EventSourcingConfiguration): UserRegistrationModule = {
    val eventSourceExecutionContext = EventSourceExecutionContextProvider.create()
    val domainApi: DomainCommandApi[UserRegistrationDomain] = eventSourceExecutionContext.assemblyDomainApplication(new UserRegistrationDomainModule())

    new UserRegistrationModule {
      override def cmdApi: DomainCommandApi[UserRegistrationDomain] = domainApi
    }

  }
}
