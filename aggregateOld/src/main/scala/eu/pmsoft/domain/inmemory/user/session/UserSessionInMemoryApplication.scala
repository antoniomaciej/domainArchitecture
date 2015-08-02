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

package eu.pmsoft.domain.inmemory.user.session

import java.util.concurrent.atomic.AtomicInteger

import eu.pmsoft.domain.inmemory.AbstractAtomicEventStoreWithProjectionInMemory
import eu.pmsoft.domain.model.security.password.reset._
import eu.pmsoft.domain.model.user.registry._
import eu.pmsoft.domain.model.user.session._
import eu.pmsoft.mcomponents.eventsourcing.ApplicationContextProvider
import monocle.macros.GenLens
import org.jasypt.util.password.BasicPasswordEncryptor

import scala.concurrent.ExecutionContext

class UserSessionInMemoryApplication(implicit val executionContext: ExecutionContext)
  extends UserSessionApplication {

  lazy val storeAndProjection = new UserSessionStoreAndProjection()

  override lazy val sideEffects: UserSessionSideEffect = new ThreadLocalUserSessionSideEffect()

  override def applicationContextProvider: ApplicationContextProvider[UserSessionEvent, UserSessionAggregate, UserSessionSSOState] =
    new ApplicationContextProvider(storeAndProjection, storeAndProjection)
}


class ThreadLocalUserSessionSideEffect extends UserSessionSideEffect {

  val counter = new AtomicInteger(0)

  val encryptor = new BasicPasswordEncryptor()

  override def generateSessionToken(userId: UserID): SessionToken = {
    val secret = encryptor.encryptPassword("sessionToken" + userId.id + ":" + counter.addAndGet(1))
    SessionToken(secret)
  }
}

class UserSessionStoreAndProjection extends
AbstractAtomicEventStoreWithProjectionInMemory[UserSessionEvent, UserSessionAggregate, UserSessionStateInMemory] {

  override def buildInitialState(): UserSessionStateInMemory = UserSessionStateInMemory()

  override def projectSingleEvent(state: UserSessionStateInMemory, event: UserSessionEvent):
  UserSessionStateInMemory = event match {
    case UserSessionCreated(sessionToken, userId) => state.createSession(sessionToken, userId)
    case UserSessionInvalidated(sessionToken, userId) => state.deleteSession(sessionToken, userId)
  }
}

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
