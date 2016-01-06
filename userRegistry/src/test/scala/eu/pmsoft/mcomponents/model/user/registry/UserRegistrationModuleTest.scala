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

package eu.pmsoft.mcomponents.model.user.registry

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreRead
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.test.{ Mocked, BaseEventSourceSpec, CommandGenerator, GeneratedCommandSpecification }

class UserRegistrationModuleTest extends BaseEventSourceSpec with GeneratedCommandSpecification[UserRegistrationDomain] {

  override def backendStrategy: EventStoreBackendStrategy[UserRegistrationDomain] = EventStoreInMemory(UserRegistrationDomainModule.eventStoreReference)

  override def bindingInfrastructure: BindingInfrastructure = LocalBindingInfrastructure.create()

  implicit def eventSourceExecutionContext: EventSourceExecutionContext = EventSourceExecutionContextProvider.create()

  override def implementationModule(): DomainModule[UserRegistrationDomain] = new UserRegistrationDomainModule()

  it should "not allow duplicated login names" in {
    val (api, _) = createApiAndDomainModule()
    val commands = List(AddUser(UserLogin("test@mail.com"), UserPassword("password")), AddUser(UserLogin("test@mail.com"), UserPassword("anyOther")))
    val serialExecutions = serial(commands)(api.commandHandler.execute)
    whenReady(serialExecutions) { results =>
      results.size should be(2)
      results.head should be(\/-) withClue ": The first command should success"
      results.tail.head should be(-\/) withClue ": The second command should fail"
    }
  }

  it should "not allow invalid emails as user login" in {
    val (api, _) = createApiAndDomainModule()
    whenReady(api.commandHandler.execute(AddUser(UserLogin("invalidEmail"), UserPassword("password")))) { result =>
      result should be(-\/) withClue ": Validation should reject the AddUser command"
    }
  }

  it should "not allow empty emails as user login" in {
    val (api, _) = createApiAndDomainModule()
    whenReady(api.commandHandler.execute(AddUser(UserLogin(""), UserPassword("password")))) { result =>
      result should be(-\/) withClue ": Validation should reject the AddUser command"
    }
  }

  it should "fail to update password for not existing users" in {
    val (api, _) = createApiAndDomainModule()
    whenReady(api.commandHandler.execute(UpdateUserPassword(UserID(0), UserPassword("AnyPassword")))) { result =>
      result should be(-\/) withClue ": Validation should reject the non existing userID"
    }
  }
  it should "fail to update active status for not existing users" in {
    val (api, _) = createApiAndDomainModule()
    whenReady(api.commandHandler.execute(UpdateActiveUserStatus(UserID(0), active = false))) { result =>
      result should be(-\/) withClue ": Validation should reject the non existing userID"
    }
  }

  override def buildGenerator(state: AtomicEventStoreView[UserRegistrationState])(implicit eventStoreRead: EventStoreRead[UserRegistrationDomain]): CommandGenerator[UserRegistrationCommand] = new UserRegistrationGenerators(state)

  override def postCommandValidation(
    state:   UserRegistrationState,
    command: UserRegistrationCommand,
    result:  EventSourceCommandConfirmation[UserRegistrationAggregate]
  )(implicit eventStoreRead: EventStoreRead[UserRegistrationDomain]): Unit = command match {
    case UpdateActiveUserStatus(uid, active) =>
      val user = result.rootAggregate match {
        case userAggId @ UserAggregateId(_) => UserRegistrationAggregates.buildUser(userAggId)
        case EmailAggregateId(_)            => fail("bad aggregate")
      }
      user.profile.value.active shouldBe active
    case AddUser(loginEmail, passwordHash) =>
      val user = result.rootAggregate match {
        case userAggId @ UserAggregateId(_) => UserRegistrationAggregates.buildUser(userAggId)
        case EmailAggregateId(_)            => fail("bad aggregate")
      }
      user.profile.value.login shouldBe loginEmail
      user.profile.value.passwordHash shouldBe passwordHash
    case UpdateUserPassword(uid, passwordHash) =>
      val user = result.rootAggregate match {
        case userAggId @ UserAggregateId(_) => UserRegistrationAggregates.buildUser(userAggId)
        case EmailAggregateId(_)            => fail("bad aggregate")
      }
      user.profile.value.passwordHash shouldBe passwordHash
    case UpdateUserRoles(uid, roles) =>
      val user = result.rootAggregate match {
        case userAggId @ UserAggregateId(_) => UserRegistrationAggregates.buildUser(userAggId)
        case EmailAggregateId(_)            => fail("bad aggregate")
      }
      user.profile.value.roles should equal(roles)
  }

  override def validateState(state: UserRegistrationState)(implicit eventStoreRead: EventStoreRead[UserRegistrationDomain]): Unit = {
    findInconsistentUid(state) shouldBe empty withClue ": UserID registered but marked as not existing"
    findMissingUsers(state) shouldBe empty withClue ": Can not find used for the given userId"
    findUsersThatCanNotLogin(state) shouldBe empty withClue ": User exists but login marked as not existing"
  }

  private def findInconsistentUid(state: UserRegistrationState): List[UserID] = {
    state
      .getAllUid
      .filter(uid => !state.uidExists(uid))
      .toList
  }

  private def findMissingUsers(state: UserRegistrationState): List[UserID] = {
    state
      .getAllUid
      .filter(uid => state.getUserByID(uid).isEmpty)
      .toList
  }

  private def findUsersThatCanNotLogin(state: UserRegistrationState): List[User] = {
    state
      .getAllUid
      .filter(uid => state.getUserByID(uid).isEmpty)
      .map(uid => state.getUserByID(uid).get)
      .filter(user => state.loginExists(user.login))
      .toList
  }

}
