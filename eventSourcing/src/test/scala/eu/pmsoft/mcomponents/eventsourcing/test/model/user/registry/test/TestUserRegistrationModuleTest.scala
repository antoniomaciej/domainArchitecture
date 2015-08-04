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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry.test

import eu.pmsoft.domain.model.{BaseEventSourceSpec, CommandGenerator, GeneratedCommandSpecification}
import eu.pmsoft.mcomponents.eventsourcing.AtomicEventStoreProjectionView
import eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry._

abstract class TestUserRegistrationModuleTest[M] extends BaseEventSourceSpec with
GeneratedCommandSpecification[TestUserRegistrationCommand, TestUserRegistrationEvent, TestUserRegistrationState, M] {

  it should "not allow duplicated login names" in {
    val module = createEmptyModule()
    val commands = List(TestAddUser(TestUserLogin("test@mail.com"), TestUserPassword("password")),
      TestAddUser(TestUserLogin("test@mail.com"), TestUserPassword("anyOther")))
    val serialExecutions = serial(commands)(asyncCommandHandler(module).execute)
    whenReady(serialExecutions) { results =>
      results.size should be(2)
      results.head should be(\/-) withClue ": The first command should success"
      results.tail.head should be(-\/) withClue ": The second command should fail"
    }
  }

  it should "not allow invalid emails as user login" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(TestAddUser(TestUserLogin("invalidEmail"),
      TestUserPassword("password")))) { result =>
      result should be(-\/) withClue ": Validation should reject the AddUser command"
    }
  }

  it should "not allow empty emails as user login" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(TestAddUser(TestUserLogin(""), TestUserPassword("password")))) { result =>
      result should be(-\/) withClue ": Validation should reject the AddUser command"
    }
  }

  it should "fail to update password for not existing users" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(TestUpdateUserPassword(TestUserID(0), TestUserPassword("AnyPassword")))) { result =>
      result should be(-\/) withClue ": Validation should reject the non existing userID"
    }
  }
  it should "fail to update active status for not existing users" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(TestUpdateActiveUserStatus(TestUserID(0), active = false))) { result =>
      result should be(-\/) withClue ": Validation should reject the non existing userID"
    }
  }

  override def buildGenerator(state: AtomicEventStoreProjectionView[TestUserRegistrationState]):
  CommandGenerator[TestUserRegistrationCommand] = new TestUserRegistrationGenerators(state)

  def postCommandValidation(state: TestUserRegistrationState, command: TestUserRegistrationCommand): Unit = command match {
    case TestUpdateActiveUserStatus(uid, active) =>
      state.getUserByID(uid).get.activeStatus shouldBe active
    case TestAddUser(loginEmail, passwordHash) =>
      state.getAllUid
        .map(state.getUserByID).map(_.get)
        .find(user => user.login == loginEmail && user.passwordHash == passwordHash) should not be empty
    case TestUpdateUserPassword(uid, passwordHash) =>
      state.getUserByID(uid) should not be empty
      state.getUserByID(uid).get.passwordHash should be(passwordHash)
    case TestUpdateUserRoles(uid, roles) =>
      state.getUserByID(uid).get.roles should equal(roles)
  }

  def validateState(state: TestUserRegistrationState): Unit = {
    findInconsistentUid(state) shouldBe empty withClue ": UserID registered but marked as not existing"
    findMissingUsers(state) shouldBe empty withClue ": Can not find used for the given userId"
    findUsersThatCanNotLogin(state) shouldBe empty withClue ": User exists but login marked as not existing"
  }

  private def findInconsistentUid(state: TestUserRegistrationState): List[TestUserID] = {
    state
      .getAllUid
      .filter(uid => !state.uidExists(uid))
      .toList
  }

  private def findMissingUsers(state: TestUserRegistrationState): List[TestUserID] = {
    state
      .getAllUid
      .filter(uid => state.getUserByID(uid).isEmpty)
      .toList
  }

  private def findUsersThatCanNotLogin(state: TestUserRegistrationState): List[TestUser] = {
    state
      .getAllUid
      .filter(uid => state.getUserByID(uid).isEmpty)
      .map(uid => state.getUserByID(uid).get)
      .filter(user => state.loginExists(user.login))
      .toList
  }


}
