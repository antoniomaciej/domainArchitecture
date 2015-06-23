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

package eu.pmsoft.domain.model.user.session

import eu.pmsoft.domain.model.security.password.reset.SessionToken
import eu.pmsoft.domain.model.user.registry.UserID
import eu.pmsoft.domain.model.{EventSourceCommandError, EventSourceModelError}

object UserSessionModel {

  val notExistingSessionForTokenErrorCode = 3001L
  val notExistingSessionForToken = EventSourceModelError("session do not exist for the given session token",
    EventSourceCommandError(notExistingSessionForTokenErrorCode))

  val notExistingSessionForUserErrorCode = 3002L
  val notExistingSessionForUser = EventSourceModelError("session do not exist for the given user",
    EventSourceCommandError(notExistingSessionForUserErrorCode))


  val criticalUserIdNotInTransactionScopeErrorCode = 3010L
  val criticalUserIdNotInTransactionScope = EventSourceModelError("Critical: UserId not found in transaction scope",
    EventSourceCommandError(criticalUserIdNotInTransactionScopeErrorCode))

  val criticalDoubleUserIdInTransactionScopeErrorCode = 3011L
  val criticalDoubleUserIdInTransactionScope = EventSourceModelError("Critical: two or more UserId found in transaction scope",
    EventSourceCommandError(criticalDoubleUserIdInTransactionScopeErrorCode))

  val criticalSessionNotFoundAfterSuccessCommandErrorCode = 3012L
  val criticalSessionNotFoundAfterSuccessCommand = EventSourceModelError("Critical: the user session was not found after login command success",
    EventSourceCommandError(criticalSessionNotFoundAfterSuccessCommandErrorCode))
}

case class UserSession(sessionToken: SessionToken, userId: UserID)

//aggregate
sealed trait UserSessionAggregate

case class UserSessionUserIDAggregate(userId: UserID) extends UserSessionAggregate

//commands
sealed trait UserSessionCommand

case class CreateUserSession(userId: UserID) extends UserSessionCommand

case class InvalidateSession(sessionToken: SessionToken) extends UserSessionCommand

case class InvalidateUserSession(userId: UserID) extends UserSessionCommand

//events

sealed trait UserSessionEvent

case class UserSessionCreated(sessionToken: SessionToken, userId: UserID) extends UserSessionEvent

case class UserSessionInvalidated(sessionToken: SessionToken, userId: UserID) extends UserSessionEvent
