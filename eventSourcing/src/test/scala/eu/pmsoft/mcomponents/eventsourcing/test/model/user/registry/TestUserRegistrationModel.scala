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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry

import eu.pmsoft.mcomponents.eventsourcing._

object TestUserRegistrationModel {

  val invalidLoginErrorCodeTest = 4001L
  val invalidLoginTest = EventSourceModelError("invalid login",
    EventSourceCommandError(invalidLoginErrorCodeTest))

  val invalidEmailErrorCodeTest = 4002L
  val invalidEmailTest = EventSourceModelError("invalid email",
    EventSourceCommandError(invalidEmailErrorCodeTest))

  val notExistingUserIDErrorCodeTest = 4003L
  val notExistingUserIDTest = EventSourceModelError("not existing userId",
    EventSourceCommandError(notExistingUserIDErrorCodeTest))

  val loginAndPasswordMismatchErrorCodeTest = 4004L
  val loginAndPasswordMismatchTest = EventSourceModelError("login and password do not match",
    EventSourceCommandError(loginAndPasswordMismatchErrorCodeTest))
}

case class TestUserID(val id: Long) extends AnyVal

case class TestUserPassword(val passwordHash: String) extends AnyVal

case class TestUserLogin(val login: String) extends AnyVal

case class TestRoleID(roleId: Int)

//Model entities

case class TestUser(uid: TestUserID,
                login: TestUserLogin,
                passwordHash: TestUserPassword,
                activeStatus: Boolean = false,
                roles: Set[TestRoleID] = Set())

//Aggregate
sealed trait TestUserRegistrationAggregate

case class TestUserAggregateId(uid: TestUserID) extends TestUserRegistrationAggregate
case class EmailAggregateIdTest(loginEmail: TestUserLogin) extends TestUserRegistrationAggregate

//UserRegistrationModel commands

sealed trait TestUserRegistrationCommand

case class TestAddUser(loginEmail: TestUserLogin, passwordHash: TestUserPassword) extends TestUserRegistrationCommand

case class TestUpdateUserPassword(uid: TestUserID, passwordHash: TestUserPassword) extends TestUserRegistrationCommand

case class TestUpdateActiveUserStatus(uid: TestUserID, active: Boolean) extends TestUserRegistrationCommand

case class TestUpdateUserRoles(uid: TestUserID, roles: Set[TestRoleID]) extends TestUserRegistrationCommand


//UserRegistrationModel events

sealed trait TestUserRegistrationEvent

case class TestUserCreated(uid: TestUserID,
                       login: TestUserLogin,
                       passwordHash: TestUserPassword) extends TestUserRegistrationEvent

case class TestUserPasswordUpdated(userId: TestUserID, passwordHash: TestUserPassword) extends TestUserRegistrationEvent

case class TestUserActiveStatusUpdated(userId: TestUserID, active: Boolean) extends TestUserRegistrationEvent

case class TestUserObtainedAccessRoles(userId: TestUserID, roles: Set[TestRoleID]) extends TestUserRegistrationEvent

