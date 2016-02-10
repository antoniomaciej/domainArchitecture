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

import eu.pmsoft.domain.model.{ SessionToken, UserID, UserSession }
import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.model.user.session.UserSessionModel._

import scalaz.{ -\/, \/- }

final class UserSessionSSODomain extends DomainSpecification {
  type Command = UserSessionCommand
  type Event = UserSessionEvent
  type Aggregate = UserSessionAggregate
  type State = UserSessionSSOState
  type SideEffects = UserSessionSideEffect
}

trait UserSessionSSOState {

  def findAllUserSessions(): Stream[UserSession]

  def findUserSession(userId: UserID): Option[UserSession]

  def findUserSession(sessionToken: SessionToken): Option[UserSession]

}

trait UserSessionSideEffect {
  def generateSessionToken(userId: UserID): SessionToken

}

final class UserSessionEventSerializationSchema extends EventSerializationSchema[UserSessionSSODomain] {
  override def mapToEvent(data: EventDataWithNr): UserSessionEvent = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    data.eventBytes.unpickle[UserSessionEvent]
  }

  override def buildConstraintReference(constraintScope: UserSessionSSODomain#ConstraintScope): ConstraintReference = ConstraintReference.noConstraintsOnDomain

  override def buildAggregateReference(aggregate: UserSessionAggregate): AggregateReference =
    aggregate match {
      case UserSessionUserIDAggregate(userId) => AggregateReference(0, userId.id)
    }

  override def eventToData(event: UserSessionEvent): EventData = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    EventData(event.pickle.value)
  }

}

final class UserSessionHandlerLogic extends DomainLogic[UserSessionSSODomain] with UserSessionValidations with UserSessionExtractors {

  override def calculateRootAggregate(command: UserSessionCommand, state: UserSessionSSOState): CommandToAggregateScope[UserSessionSSODomain] =
    command match {
      case CreateUserSession(userId) => \/-(Set(UserSessionUserIDAggregate(userId)))
      case InvalidateSession(sessionToken) => state.findUserSession(sessionToken) match {
        case Some(session) => \/-(Set(UserSessionUserIDAggregate(session.userId)))
        case None          => -\/(EventSourceCommandFailed(notExistingSessionForToken.code))
      }
      case InvalidateUserSession(userId) => \/-(Set(UserSessionUserIDAggregate(userId)))
    }

  override def executeCommand(
    command:                UserSessionCommand,
    atomicTransactionScope: AtomicTransactionScope[UserSessionSSODomain]
  )(implicit state: UserSessionSSOState, sideEffects: UserSessionSideEffect): CommandToEventsResult[UserSessionSSODomain] =
    command match {
      case CreateUserSession(userId) =>
        val sessionToken = sideEffects.generateSessionToken(userId)
        val events = state.findUserSession(userId) match {
          case Some(session) =>
            List(
              UserSessionInvalidated(session.sessionToken, session.userId),
              UserSessionCreated(sessionToken, userId)
            )
          case None =>
            List(
              UserSessionCreated(sessionToken, userId)
            )
        }
        \/-(CommandModelResult(events, UserSessionUserIDAggregate(userId)))
      case InvalidateSession(sessionToken) => for {
        userId <- extractUserId(atomicTransactionScope.aggregateVersion)
      } yield CommandModelResult(List(UserSessionInvalidated(sessionToken, userId)), UserSessionUserIDAggregate(userId))

      case InvalidateUserSession(userId) => for {
        session <- sessionExist(userId)
      } yield CommandModelResult(List(UserSessionInvalidated(session.sessionToken, session.userId)), UserSessionUserIDAggregate(userId))

    }

}

trait UserSessionExtractors {

  def extractUserId(transactionScope: Map[UserSessionAggregate, Long]): CommandPartialValidation[UserID] =
    transactionScope.keys.map {
      case UserSessionUserIDAggregate(userId) => userId
    }.toList match {
      case Nil           => -\/(EventSourceCommandFailed(criticalUserIdNotInTransactionScope.code))
      case userId :: Nil => \/-(userId)
      case _             => -\/(EventSourceCommandFailed(criticalDoubleUserIdInTransactionScope.code))
    }
}

trait UserSessionValidations {

  def sessionExist(userId: UserID)(implicit state: UserSessionSSOState): CommandPartialValidation[UserSession] =
    state.findUserSession(userId) match {
      case Some(session) => \/-(session)
      case None          => -\/(EventSourceCommandFailed(notExistingSessionForUser.code))
    }

}

