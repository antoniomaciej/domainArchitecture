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
 */

package eu.pmsoft.mcomponents.model.user.session.inmemory

import eu.pmsoft.domain.model.{SessionToken, UserID, UserSession}
import eu.pmsoft.mcomponents.model.user.session._
import monocle.macros.GenLens

import scala.language.higherKinds

object UserSessionStateInMemoryLenses {
  val stateLens = GenLens[UserSessionStateInMemory]
  val _activeSessions = stateLens(_.activeSessions)
  val _sessionForUser = stateLens(_.sessionForUser)
}


case class UserSessionStateInMemory(activeSessions: Map[SessionToken, UserSession] = Map(),
                                    sessionForUser: Map[UserID, SessionToken] = Map()) extends UserSessionSSOState {

  override def findUserSession(userId: UserID): Option[UserSession] = for {
    sessionToken <- sessionForUser.get(userId)
    session <- activeSessions.get(sessionToken)
  } yield session

  override def findUserSession(sessionToken: SessionToken): Option[UserSession] = activeSessions.get(sessionToken)

  override def findAllUserSessions(): Stream[UserSession] = activeSessions.values.toStream

  import UserSessionStateInMemoryLenses._

  def createSession(sessionToken: SessionToken, userId: UserID): UserSessionStateInMemory = {
    val operation = _activeSessions.modify {
      _ ++ Map(sessionToken -> UserSession(sessionToken, userId))
    } compose _sessionForUser.modify {
      _ ++ Map(userId -> sessionToken)
    }
    operation(this)
  }

  def deleteSession(sessionToken: SessionToken, userId: UserID): UserSessionStateInMemory = {
    val operation = _activeSessions.modify {
      _ - sessionToken
    } compose _sessionForUser.modify {
      _ - userId
    }
    operation(this)
  }
}
