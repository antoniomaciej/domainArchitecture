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

package eu.pmsoft.mcomponents.model.user.session

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.AtomicEventStoreProjectionView

abstract class UserSessionModuleTest[M] extends BaseEventSourceSpec with
GeneratedCommandSpecification[UserSessionCommand, UserSessionEvent, UserSessionSSOState, M] {
  override def buildGenerator(state: AtomicEventStoreProjectionView[UserSessionSSOState]):
  CommandGenerator[UserSessionCommand] = new UserSessionGenerators(state)

  override def validateState(state: UserSessionSSOState): Unit = {
    findInconsistentSessionByToken(state) shouldBe empty withClue ": Session exist but can not be found by token"
    findInconsistentSessionByUserID(state) shouldBe empty withClue ": Session exist but can not be found by userId"
  }

  private def findInconsistentSessionByToken(state: UserSessionSSOState): Stream[UserSession] = {
    state
      .findAllUserSessions()
      .filter(session => state.findUserSession(session.sessionToken).isEmpty)
  }

  private def findInconsistentSessionByUserID(state: UserSessionSSOState): Stream[UserSession] = {
    state
      .findAllUserSessions()
      .filter(session => state.findUserSession(session.userId).isEmpty)
  }

  override def postCommandValidation(state: UserSessionSSOState, command: UserSessionCommand): Unit = command match {
    case CreateUserSession(userId) =>
      state.findUserSession(userId) should not be empty withClue ": Session not created"
    case InvalidateSession(sessionToken) =>
      state.findUserSession(sessionToken) shouldBe empty withClue ": Session not invalidated"
    case InvalidateUserSession(userId) =>
      state.findUserSession(userId) shouldBe empty withClue ": Session not invalidated"
  }
}
