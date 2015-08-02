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

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel._
import eu.pmsoft.domain.model.security.password.reset._
import eu.pmsoft.domain.model.user.registry._
import UserSessionModel._
import scalaz.{-\/, \/-}

trait UserSessionModule {

}

trait UserSessionSSOState {

  def findAllUserSessions(): Stream[UserSession]

  def findUserSession(userId: UserID): Option[UserSession]

  def findUserSession(sessionToken: SessionToken): Option[UserSession]

}

trait UserSessionSideEffect {
  def generateSessionToken(userId: UserID): SessionToken

}

final class UserSessionCommandToTransactionScope extends CommandToTransactionScope[UserSessionCommand, UserSessionAggregate, UserSessionSSOState] {
  override def calculateTransactionScope(command: UserSessionCommand, state: UserSessionSSOState): CommandToAggregateResult[UserSessionAggregate] =
    command match {
      case CreateUserSession(userId) => \/-(Set(UserSessionUserIDAggregate(userId)))
      case InvalidateSession(sessionToken) => state.findUserSession(sessionToken) match {
        case Some(session) => \/-(Set(UserSessionUserIDAggregate(session.userId)))
        case None => -\/(EventSourceCommandFailed(notExistingSessionForToken.code))
      }
      case InvalidateUserSession(userId) => \/-(Set(UserSessionUserIDAggregate(userId)))
    }
}

final class UserSessionHandlerLogic(val sideEffects: UserSessionSideEffect) extends
DomainLogic[UserSessionCommand, UserSessionEvent, UserSessionAggregate, UserSessionSSOState] with
UserSessionValidations with UserSessionExtractors {

  override def executeCommand(command: UserSessionCommand,
                              transactionScope: Map[UserSessionAggregate, Long])
                             (implicit state: UserSessionSSOState):
  CommandToEventsResult[UserSessionEvent] = command match {
    case CreateUserSession(userId) =>
      state.findUserSession(userId) match {
        case Some(session) =>
          \/-(List(
            UserSessionInvalidated(session.sessionToken, session.userId),
            UserSessionCreated(sideEffects.generateSessionToken(userId), userId)
          ))
        case None =>
          \/-(List(
            UserSessionCreated(sideEffects.generateSessionToken(userId), userId)
          ))
      }
    case InvalidateSession(sessionToken) => for {
      userId <- extractUserId(transactionScope)
    } yield List(UserSessionInvalidated(sessionToken, userId))
    case InvalidateUserSession(userId) => for {
      session <- sessionExist(userId)
    } yield List(UserSessionInvalidated(session.sessionToken, session.userId))
  }


}

trait UserSessionExtractors {

  def extractUserId(transactionScope: Map[UserSessionAggregate, Long]): CommandPartialValidation[UserID] =
    transactionScope.keys.map {
      case UserSessionUserIDAggregate(userId) => userId
    }.toList match {
      case Nil => -\/(EventSourceCommandFailed(criticalUserIdNotInTransactionScope.code))
      case userId :: Nil => \/-(userId)
      case _ => -\/(EventSourceCommandFailed(criticalDoubleUserIdInTransactionScope.code))
    }
}

trait UserSessionValidations {

  def sessionExist(userId: UserID)
                  (implicit state: UserSessionSSOState): CommandPartialValidation[UserSession] =
    state.findUserSession(userId) match {
      case Some(session) => \/-(session)
      case None => -\/(EventSourceCommandFailed(notExistingSessionForUser.code))
    }

}

