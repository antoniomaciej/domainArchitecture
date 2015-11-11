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
import eu.pmsoft.mcomponents.eventsourcing.AtomicEventStoreView
import eu.pmsoft.mcomponents.test.{BaseEventSourceSpec, CommandGenerator, GeneratedCommandSpecification}

abstract class UserRegistrationModuleTest extends BaseEventSourceSpec with
GeneratedCommandSpecification[UserRegistrationDomain, UserRegistrationApplication] {

  def infrastructure(): UserRegistrationApplicationInfrastructure

  it should "not allow duplicated login names" in {
    val module = createEmptyModule()
    val commands = List(AddUser(UserLogin("test@mail.com"), UserPassword("password")), AddUser(UserLogin("test@mail.com"), UserPassword("anyOther")))
    val serialExecutions = serial(commands)(asyncCommandHandler(module).execute)
    whenReady(serialExecutions) { results =>
      results.size should be(2)
      results.head should be(\/-) withClue ": The first command should success"
      results.tail.head should be(-\/) withClue ": The second command should fail"
    }
  }

  it should "not allow invalid emails as user login" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(AddUser(UserLogin("invalidEmail"), UserPassword("password")))) { result =>
      result should be(-\/) withClue ": Validation should reject the AddUser command"
    }
  }

  it should "not allow empty emails as user login" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(AddUser(UserLogin(""), UserPassword("password")))) { result =>
      result should be(-\/) withClue ": Validation should reject the AddUser command"
    }
  }

  it should "fail to update password for not existing users" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(UpdateUserPassword(UserID(0), UserPassword("AnyPassword")))) { result =>
      result should be(-\/) withClue ": Validation should reject the non existing userID"
    }
  }
  it should "fail to update active status for not existing users" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(UpdateActiveUserStatus(UserID(0), active = false))) { result =>
      result should be(-\/) withClue ": Validation should reject the non existing userID"
    }
  }


  override def createEmptyModule(): UserRegistrationApplication = UserRegistrationApplication.createApplication(infrastructure())

  override def buildGenerator(state: AtomicEventStoreView[UserRegistrationState]):
  CommandGenerator[UserRegistrationCommand] = new UserRegistrationGenerators(state)

  def postCommandValidation(state: UserRegistrationState, command: UserRegistrationCommand): Unit = command match {
    case UpdateActiveUserStatus(uid, active) =>
      state.getUserByID(uid).get.activeStatus shouldBe active
    case AddUser(loginEmail, passwordHash) =>
      state.getAllUid
        .map(state.getUserByID).map(_.get)
        .find(user => user.login == loginEmail && user.passwordHash == passwordHash) should not be empty
    case UpdateUserPassword(uid, passwordHash) =>
      state.getUserByID(uid) should not be empty
      state.getUserByID(uid).get.passwordHash should be(passwordHash)
    case UpdateUserRoles(uid, roles) =>
      state.getUserByID(uid).get.roles should equal(roles)
  }

  def validateState(state: UserRegistrationState): Unit = {
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
