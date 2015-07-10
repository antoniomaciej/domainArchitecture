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

import eu.pmsoft.domain.model.user.registry.UserID
import eu.pmsoft.domain.model.{AtomicEventStoreProjection, CommandGenerator}
import org.scalacheck.Gen

class UserSessionGenerators(val state: AtomicEventStoreProjection[UserSessionSSOState]) extends CommandGenerator[UserSessionCommand] {
  override def generateSingleCommands: Gen[UserSessionCommand] = Gen.frequency(
    (3, genCreateUserSession),
    (1, genInvalidateSession),
    (1, genInvalidateUserSession)
  )

  override def generateWarmUpCommands: Gen[List[UserSessionCommand]] = Gen.nonEmptyListOf(genCreateUserSession)

  def genExistingSession(): Gen[UserSession] = Gen.wrap(
    Gen.oneOf(state.lastSnapshot().futureValue.findAllUserSessions())
  )

  lazy val genCreateUserSession = for {
    userId <- genUserID
  } yield CreateUserSession(userId)

  lazy val genInvalidateSession = for {
    sessionToken <- genExistingSession()
  } yield InvalidateSession(sessionToken.sessionToken)

  lazy val genInvalidateUserSession = for {
    sessionToken <- genExistingSession()
  } yield InvalidateUserSession(sessionToken.userId)

  val minUserId = 0L
  val maxUserId = 100L
  lazy val genUserID = for {
    id <- Gen.choose(minUserId, maxUserId)
  } yield UserID(id)

}
