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

package eu.pmsoft.mcomponents.model.user.registry.inmemory

import java.util.concurrent.atomic.AtomicLong

import eu.pmsoft.domain.inmemory.AbstractAtomicEventStoreWithProjectionInMemory
import eu.pmsoft.domain.model.{UserID, UserLogin, UserPassword}
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.model.security.roles._
import eu.pmsoft.mcomponents.model.user.registry._

import scala.concurrent.ExecutionContext


class UserRegistrationInMemoryApplication(implicit val executionContext: ExecutionContext)
  extends UserRegistrationApplication {

  override val sideEffects: UserRegistrationLocalSideEffects = new LocalThreadUserRegistrationLocalSideEffects()

  private val storeAndProjectionInMemory = new UserRegistrationInMemoryEventStore()

  override val applicationContextProvider: ApplicationContextProvider[UserRegistrationEvent, UserRegistrationAggregate, UserRegistrationState] =
    new ApplicationContextProvider(storeAndProjectionInMemory, storeAndProjectionInMemory)

}

class LocalThreadUserRegistrationLocalSideEffects extends UserRegistrationLocalSideEffects {
  val uidCounter = new AtomicLong(0)

  override def createNextUid(): UserID = new UserID(uidCounter.getAndAdd(1))

}


class UserRegistrationInMemoryEventStore
  extends AbstractAtomicEventStoreWithProjectionInMemory[UserRegistrationEvent, UserRegistrationAggregate, UserRegistrationStateInMemory] {

  override def buildInitialState(): UserRegistrationStateInMemory = UserRegistrationStateInMemory()

  override def projectSingleEvent(state: UserRegistrationStateInMemory, event: UserRegistrationEvent): UserRegistrationStateInMemory = event match {
    case UserPasswordUpdated(userId, passwordHash) => state.updatePassword(userId, passwordHash)
    case UserActiveStatusUpdated(userId, active) => state.activation(userId, active)
    case UserCreated(uid, login, passwordHash) => state.createUser(uid, login, passwordHash)
    case UserObtainedAccessRoles(userId, roles) => state.changeUserRoles(userId, roles)
  }

}

import monocle.function._
import monocle.macros.GenLens
import monocle.std._

import scala.language.{higherKinds, postfixOps}

object UserRegistrationStateInMemoryLenses {

  val stateGenLen = GenLens[UserRegistrationStateInMemory]
  val _userByID = stateGenLen(_.userByID)
  val _loginToUserID = stateGenLen(_.loginToUserID)
  val _userRolesMap = stateGenLen(_.userRolesMap)
  val _version = stateGenLen(_.version)

  val userGenLens = GenLens[User]
  val _userLogin = userGenLens(_.login)
  val _userPasswordHash = userGenLens(_.passwordHash)
  val _userActiveStatus = userGenLens(_.activeStatus)
  val _userRoles = userGenLens(_.roles)
}

case class UserRegistrationStateInMemory(
                                          userByID: Map[UserID, User] = Map(),
                                          loginToUserID: Map[UserLogin, UserID] = Map(),
                                          userRolesMap: Map[UserID, Set[RoleID]] = Map(),
                                          version: EventStoreVersion = EventStoreVersion(0)
                                          ) extends UserRegistrationState {


  import UserRegistrationStateInMemoryLenses._

  override def loginExists(login: UserLogin): Boolean = userByID.values.exists(_.login == login)

  override def uidExists(uid: UserID): Boolean = userByID.keySet.contains(uid)

  override def getUserByLogin(login: UserLogin): Option[User] = loginToUserID.get(login).flatMap(getUserByID)

  override def getUserByID(uid: UserID): Option[User] = userByID.get(uid)

  override def getAllUid: Stream[UserID] = userByID.keys.toStream

  def createUser(uid: UserID, login: UserLogin, passwordHash: UserPassword): UserRegistrationStateInMemory = {
    val updateUserId = (_userByID ^|-> at(uid)).set(Some(User(uid, login, passwordHash)))
    val userByLoginMap = (_loginToUserID ^|-> at(login)).set(Some(uid))
    updateUserId.compose(userByLoginMap)(this)
  }


  def changeUserRoles(userId: UserID, roles: Set[RoleID]): UserRegistrationStateInMemory =
    (_userByID ^|-? index(userId) ^|-> _userRoles).set(roles)(this)

  def activation(userId: UserID, active: Boolean): UserRegistrationStateInMemory =
    (_userByID ^|-? index(userId) ^|-> _userActiveStatus).set(active)(this)

  def updatePassword(userId: UserID, passwordHash: UserPassword): UserRegistrationStateInMemory =
    (_userByID ^|-? index(userId) ^|-> _userPasswordHash).set(passwordHash)(this)
}
