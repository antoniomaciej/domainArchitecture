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
 */

package eu.pmsoft.domain.model.user.session

import eu.pmsoft.domain.model.{EventSourceCommandError, EventSourceModelError}
import eu.pmsoft.domain.model.security.password.reset.SessionToken
import eu.pmsoft.domain.model.userRegistry.UserID

object UserSessionModel {

  val notExistingSessionForToken = EventSourceModelError("session do not exist for the given session token", EventSourceCommandError(100L))
  val notExistingSessionForUser = EventSourceModelError("session do not exist for the given user", EventSourceCommandError(100L))
}

case class UserSession(sessionToken: SessionToken, userId: UserID)

//commands
sealed trait UserSessionCommand

case class CreateUserSession(userId: UserID) extends UserSessionCommand

case class InvalidateSession(sessionToken: SessionToken) extends UserSessionCommand

case class InvalidateUserSession(userId: UserID) extends UserSessionCommand

//events

sealed trait UserSessionEvent

case class UserSessionCreated(sessionToken: SessionToken, userId: UserID) extends UserSessionEvent

case class UserSessionInvalidated(sessionToken: SessionToken, userId: UserID) extends UserSessionEvent
