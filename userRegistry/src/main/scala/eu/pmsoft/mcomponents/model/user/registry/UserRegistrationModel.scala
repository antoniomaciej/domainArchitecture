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

import eu.pmsoft.domain.model.{ UserID, UserLogin, UserPassword }
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.model.security.roles.RoleID

object UserRegistrationModel {

  val invalidLoginErrorCode = 4001L
  val invalidLogin = EventSourceModelError(
    "invalid login",
    EventSourceCommandError(invalidLoginErrorCode)
  )

  val invalidEmailErrorCode = 4002L
  val invalidEmail = EventSourceModelError(
    "invalid email",
    EventSourceCommandError(invalidEmailErrorCode)
  )

  val notExistingUserIDErrorCode = 4003L
  val notExistingUserID = EventSourceModelError(
    "not existing userId",
    EventSourceCommandError(notExistingUserIDErrorCode)
  )

  val loginAndPasswordMismatchErrorCode = 4004L
  val loginAndPasswordMismatch = EventSourceModelError(
    "login and password do not match",
    EventSourceCommandError(loginAndPasswordMismatchErrorCode)
  )
}

//Model entities

case class User(
  uid:          UserID,
  login:        UserLogin,
  passwordHash: UserPassword,
  activeStatus: Boolean      = false,
  roles:        Set[RoleID]  = Set()
)

//Aggregate
sealed trait UserRegistrationAggregate

case class UserAggregateId(uid: UserID) extends UserRegistrationAggregate

case class EmailAggregateId(loginEmail: UserLogin) extends UserRegistrationAggregate

//UserRegistrationModel commands

sealed trait UserRegistrationCommand

case class AddUser(loginEmail: UserLogin, passwordHash: UserPassword) extends UserRegistrationCommand

case class UpdateUserPassword(uid: UserID, passwordHash: UserPassword) extends UserRegistrationCommand

case class UpdateActiveUserStatus(uid: UserID, active: Boolean) extends UserRegistrationCommand

case class UpdateUserRoles(uid: UserID, roles: Set[RoleID]) extends UserRegistrationCommand

//UserRegistrationModel events

sealed trait UserRegistrationEvent

case class UserCreated(
  uid:          UserID,
  login:        UserLogin,
  passwordHash: UserPassword
) extends UserRegistrationEvent

case class UserPasswordUpdated(userId: UserID, passwordHash: UserPassword) extends UserRegistrationEvent

case class UserActiveStatusUpdated(userId: UserID, active: Boolean) extends UserRegistrationEvent

case class UserObtainedAccessRoles(userId: UserID, roles: Set[RoleID]) extends UserRegistrationEvent

