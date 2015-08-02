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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry.inmemory

import java.util.concurrent.atomic.AtomicLong

import eu.pmsoft.domain.inmemory.AbstractAtomicEventStoreWithProjectionInMemory
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry._

import scala.concurrent.ExecutionContext


class TestUserRegistrationInMemoryApplication(implicit val executionContext: ExecutionContext)
  extends TestUserRegistrationApplication {

  private val storeAndProjectionInMemory = new TestUserRegistrationInMemoryEventStore()

  override val sideEffects: TestUserRegistrationLocalSideEffects = new TestLocalThreadUserRegistrationLocalSideEffects()

  override val applicationContextProvider: ApplicationContextProvider[TestUserRegistrationEvent, TestUserRegistrationAggregate, TestUserRegistrationState] =
    new ApplicationContextProvider(storeAndProjectionInMemory, storeAndProjectionInMemory)

}

class TestLocalThreadUserRegistrationLocalSideEffects extends TestUserRegistrationLocalSideEffects {
  override def createNextUid(): TestUserID = new TestUserID(uidCounter.getAndAdd(1))

  val uidCounter = new AtomicLong(0)

}


class TestUserRegistrationInMemoryEventStore
  extends AbstractAtomicEventStoreWithProjectionInMemory[TestUserRegistrationEvent, TestUserRegistrationAggregate, TestUserRegistrationStateInMemory] {

  override def buildInitialState(): TestUserRegistrationStateInMemory = TestUserRegistrationStateInMemory()

  override def projectSingleEvent(state: TestUserRegistrationStateInMemory, event: TestUserRegistrationEvent): TestUserRegistrationStateInMemory = event match {
    case TestUserPasswordUpdated(userId, passwordHash) => state.updatePassword(userId, passwordHash)
    case TestUserActiveStatusUpdated(userId, active) => state.activation(userId, active)
    case TestUserCreated(uid, login, passwordHash) => state.createUser(uid, login, passwordHash)
    case TestUserObtainedAccessRoles(userId, roles) => state.changeUserRoles(userId, roles)
  }

}

import monocle.function._
import monocle.macros.GenLens
import monocle.std._

import scala.language.{higherKinds, postfixOps}

object TestUserRegistrationStateInMemoryLenses {

  val stateGenLen = GenLens[TestUserRegistrationStateInMemory]
  val _userByID = stateGenLen(_.userByID)
  val _loginToUserID = stateGenLen(_.loginToUserID)
  val _userRolesMap = stateGenLen(_.userRolesMap)
  val _version = stateGenLen(_.version)

  val userGenLens = GenLens[TestUser]
  val _userLogin = userGenLens(_.login)
  val _userPasswordHash = userGenLens(_.passwordHash)
  val _userActiveStatus = userGenLens(_.activeStatus)
  val _userRoles = userGenLens(_.roles)
}

case class TestUserRegistrationStateInMemory(
                                          userByID: Map[TestUserID, TestUser] = Map(),
                                          loginToUserID: Map[TestUserLogin, TestUserID] = Map(),
                                          userRolesMap: Map[TestUserID, Set[TestRoleID]] = Map(),
                                          version: EventStoreVersion = EventStoreVersion(0)
                                          ) extends TestUserRegistrationState {


  import TestUserRegistrationStateInMemoryLenses._

  override def getUserByID(uid: TestUserID): Option[TestUser] = userByID.get(uid)

  override def loginExists(login: TestUserLogin): Boolean = userByID.values.exists(_.login == login)

  override def uidExists(uid: TestUserID): Boolean = userByID.keySet.contains(uid)

  override def getUserByLogin(login: TestUserLogin): Option[TestUser] = loginToUserID.get(login).flatMap(getUserByID)

  override def getAllUid: Stream[TestUserID] = userByID.keys.toStream

  def createUser(uid: TestUserID, login: TestUserLogin, passwordHash: TestUserPassword): TestUserRegistrationStateInMemory = {
    val updateUserId = (_userByID ^|-> at(uid)).set(Some(TestUser(uid, login, passwordHash)))
    val userByLoginMap =  (_loginToUserID ^|-> at(login)).set(Some(uid))
    updateUserId.compose(userByLoginMap)(this)
  }


  def changeUserRoles(userId: TestUserID, roles: Set[TestRoleID]): TestUserRegistrationStateInMemory =
    (_userByID ^|-? index(userId) ^|-> _userRoles).set(roles)(this)

  def activation(userId: TestUserID, active: Boolean): TestUserRegistrationStateInMemory =
    (_userByID ^|-? index(userId) ^|-> _userActiveStatus).set(active)(this)

  def updatePassword(userId: TestUserID, passwordHash: TestUserPassword): TestUserRegistrationStateInMemory =
    (_userByID ^|-? index(userId) ^|-> _userPasswordHash).set(passwordHash)(this)
}
