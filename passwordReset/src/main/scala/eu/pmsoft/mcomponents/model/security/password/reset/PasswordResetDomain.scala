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

package eu.pmsoft.mcomponents.model.security.password.reset

import eu.pmsoft.domain.model.{ SessionToken, UserID, UserPassword }
import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.model.security.roles.{ PermissionIdAggregate, AccessPermissionCreated }

import scala.collection.immutable.Nil
import scalaz.{ -\/, \/- }

final class PasswordResetDomain extends DomainSpecification {
  type Command = PasswordResetModelCommand
  type Event = PasswordResetModelEvent
  type Aggregate = PasswordResetAggregate
  type State = PasswordResetModelState
  type SideEffects = PasswordResetModelSideEffects
}

trait PasswordResetModelState {

  def findFlowByUserID(userId: UserID): Option[PasswordResetFlowStatus]

  def findFlowByPasswordToken(passwordResetToken: PasswordResetToken): Option[PasswordResetFlowStatus]

  def getExistingProcessUserId: Stream[UserID]

}

trait PasswordResetModelSideEffects {

  def generatePasswordResetToken(sessionToken: SessionToken): PasswordResetToken

  def validatePasswordResetToken(
    sessionToken:       SessionToken,
    passwordResetToken: PasswordResetToken
  ): Boolean
}

class PasswordResetDomainEventSerializationSchema extends EventSerializationSchema[PasswordResetDomain] {

  override def mapToEvent(data: EventDataWithNr): PasswordResetModelEvent = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    data.eventBytes.unpickle[PasswordResetModelEvent]
  }

  override def buildReference(aggregate: PasswordResetAggregate): AggregateReference = aggregate match {
    case UserIdFlowAggregate(userID) => AggregateReference(0, userID.id)
  }

  override def eventToData(event: PasswordResetModelEvent): EventData = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    EventData(event.pickle.value)
  }
}

import eu.pmsoft.mcomponents.model.security.password.reset.PasswordResetModel._

final class PasswordResetModelLogicHandler extends DomainLogic[PasswordResetDomain]
    with PasswordResetModelValidations
    with PasswordResetModelTransactionExtractor {

  override def calculateTransactionScope(command: PasswordResetModelCommand, state: PasswordResetModelState): CommandToAggregates[PasswordResetDomain] = command match {
    case InitializePasswordResetFlow(userId, sessionToken) => \/-(Set(UserIdFlowAggregate(userId)))
    case CancelPasswordResetFlowByUser(userId)             => \/-(Set(UserIdFlowAggregate(userId)))
    case CancelPasswordResetFlowByToken(passwordResetToken) => state.findFlowByPasswordToken(passwordResetToken) match {
      case Some(flow) => \/-(Set(UserIdFlowAggregate(flow.userId)))
      case None       => -\/(EventSourceCommandFailed(invalidPasswordResetToken.code))
    }
    case ConfirmPasswordResetFlow(sessionToken, passwordResetToken, newPassword) => state.findFlowByPasswordToken(passwordResetToken) match {
      case Some(flow) => \/-(Set(UserIdFlowAggregate(flow.userId)))
      case None       => -\/(EventSourceCommandFailed(invalidPasswordResetToken.code))
    }
  }

  override def executeCommand(
    command:          PasswordResetModelCommand,
    transactionScope: Map[PasswordResetAggregate, Long]
  )(implicit state: PasswordResetModelState, sideEffects: PasswordResetModelSideEffects): CommandToEventsResult[PasswordResetDomain] = command match {
    case InitializePasswordResetFlow(userId, sessionToken) => for {
      sessionTokenValid <- validateSessionToken(sessionToken)
    } yield {
      val events = state.findFlowByUserID(userId) match {
        case None => List(PasswordResetFlowCreated(userId, sessionToken, sideEffects.generatePasswordResetToken(sessionToken)))
        case Some(previousProcess) => List(
          PasswordResetFlowCancelled(userId, previousProcess.passwordResetToken),
          PasswordResetFlowCreated(userId, sessionToken, sideEffects.generatePasswordResetToken(sessionToken))
        )
      }
      CommandModelResult[PasswordResetDomain](
        events,
        UserIdFlowAggregate(userId)
      )
    }
    case CancelPasswordResetFlowByUser(userId) => for {
      passwordResetToken <- extractPasswordResetTokenForUser(userId)
      userId <- extractUserFromAggregated(transactionScope)
    } yield CommandModelResult[PasswordResetDomain](
      List(PasswordResetFlowCancelled(userId, passwordResetToken)),
      UserIdFlowAggregate(userId)
    )

    case CancelPasswordResetFlowByToken(passwordResetToken) => for {
      userId <- extractUserFromAggregated(transactionScope)
    } yield CommandModelResult[PasswordResetDomain](
      List(PasswordResetFlowCancelled(userId, passwordResetToken)),
      UserIdFlowAggregate(userId)
    )

    case ConfirmPasswordResetFlow(sessionToken, passwordResetToken, newPassword) => for {
      newPasswordValid <- validatePassword(newPassword)
      sessionTokenValid <- validateSessionToken(sessionToken)
      passwordResetTokenValid <- validateTokenPair(sessionToken, passwordResetToken)
      userId <- extractUserFromAggregated(transactionScope)
    } yield CommandModelResult[PasswordResetDomain](
      List(PasswordResetFlowConfirmed(userId, newPassword)),
      UserIdFlowAggregate(userId)
    )

  }

}

trait PasswordResetModelTransactionExtractor {

  def extractPasswordResetTokenForUser(userId: UserID)(implicit state: PasswordResetModelState): CommandPartialValidation[PasswordResetToken] = state.findFlowByUserID(userId) match {
    case Some(process) => \/-(process.passwordResetToken)
    case None          => -\/(EventSourceCommandFailed(notFoundPasswordResetToken.code))
  }

  def extractUserFromAggregated(transactionScope: Map[PasswordResetAggregate, Long]): CommandPartialValidation[UserID] =
    transactionScope.keySet.map {
      case UserIdFlowAggregate(userID) => userID
    }.toList match {
      case Nil         => -\/(EventSourceCommandFailed(criticalUserIdNotFoundInTransactionScope.code))
      case head :: Nil => \/-(head)
      case _           => -\/(EventSourceCommandFailed(criticalTwoUserIdInTransactionScope.code))
    }
}

trait PasswordResetModelValidations {

  def validateSessionToken(sessionToken: SessionToken)(implicit state: PasswordResetModelState, sideEffects: PasswordResetModelSideEffects): CommandPartialValidation[SessionToken] =
    if (!sessionToken.token.isEmpty) {
      \/-(sessionToken)
    }
    else {
      -\/(EventSourceCommandFailed(invalidSessionToken.code))
    }

  def validateTokenPair(
    sessionToken:       SessionToken,
    passwordResetToken: PasswordResetToken
  )(implicit state: PasswordResetModelState, sideEffects: PasswordResetModelSideEffects): CommandPartialValidation[PasswordResetToken] =
    if (sideEffects.validatePasswordResetToken(sessionToken, passwordResetToken)) {
      \/-(passwordResetToken)
    }
    else {
      -\/(EventSourceCommandFailed(invalidTokenPair.code))
    }

  def validatePassword(newPassword: UserPassword)(implicit state: PasswordResetModelState, sideEffects: PasswordResetModelSideEffects): CommandPartialValidation[UserPassword] =
    if (UserPasswordValidator.validate(newPassword)) {
      \/-(newPassword)
    }
    else {
      -\/(EventSourceCommandFailed(invalidPassword.code))
    }

}

object UserPasswordValidator {

  def validate(value: UserPassword): Boolean = !value.passwordHash.isEmpty

}
