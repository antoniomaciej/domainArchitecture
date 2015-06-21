/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Pawe? Cesar Sanjuan Szklarz
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

package eu.pmsoft.domain.model.security.password.reset

import eu.pmsoft.domain.model.DomainLogic
import eu.pmsoft.domain.model.EventSourceEngine._
import eu.pmsoft.domain.model.userRegistry.{UserID, UserPassword}

import scalaz.{-\/, \/-}

object PasswordResetModule {

}

trait PasswordResetModelState {

  def findFlowByUserID(userId: UserID): Option[PasswordResetFlowStatus]

  def findFlowByPasswordToken(passwordResetToken: PasswordResetToken): Option[PasswordResetFlowStatus]

  def getExistingProcessUserId: Stream[UserID]

}

trait PasswordResetModelSideEffects {

  def generatePasswordResetToken(sessionToken: SessionToken): PasswordResetToken

  def validatePasswordResetToken(sessionToken: SessionToken,
                                 passwordResetToken: PasswordResetToken): Boolean
}

final class PasswordResetModelLogicHandler(val sideEffects: PasswordResetModelSideEffects) extends
DomainLogic[PasswordResetModelCommand, PasswordResetModelEvent, PasswordResetModelState]
with PasswordResetModelValidations {

  override def executeCommand(command: PasswordResetModelCommand)
                             (implicit state: PasswordResetModelState):
  CommandToEventsResult[PasswordResetModelEvent] = command match {
    case InitializePasswordResetFlow(userId, sessionToken) => for {
      sessionTokenValid <- validateSessionToken(sessionToken)
    } yield state.findFlowByUserID(userId) match {
        case None => List(PasswordResetFlowCreated(userId, sessionToken, sideEffects.generatePasswordResetToken(sessionToken)))
        case Some(previousProcess) => List(
          PasswordResetFlowCancelled(userId, previousProcess.passwordResetToken),
          PasswordResetFlowCreated(userId, sessionToken, sideEffects.generatePasswordResetToken(sessionToken))
        )
      }
    case CancelPasswordResetFlowByUser(userId) =>
      state.findFlowByUserID(userId) match {
        case None => \/-(List())
        case Some(process) => \/-(List(PasswordResetFlowCancelled(process.userId, process.passwordResetToken)))
      }
    case CancelPasswordResetFlowByToken(passwordResetToken) =>
      state.findFlowByPasswordToken(passwordResetToken) match {
        case None => \/-(List())
        case Some(process) => \/-(List(PasswordResetFlowCancelled(process.userId, process.passwordResetToken)))
      }
    case ConfirmPasswordResetFlow(sessionToken, passwordResetToken, newPassword) => for {
      newPasswordValid <- validatePassword(newPassword)
      sessionTokenValid <- validateSessionToken(sessionToken)
      passwordResetTokenValid <- validateTokenPair(sessionToken, passwordResetToken)
    } yield state.findFlowByPasswordToken(passwordResetTokenValid) match {
        case None => List()
        case Some(process) => List(PasswordResetFlowConfirmed(process.userId, newPassword))
      }
  }
}

import eu.pmsoft.domain.model.security.password.reset.PasswordResetModel._

trait PasswordResetModelValidations {

  def sideEffects: PasswordResetModelSideEffects

  def validateSessionToken(sessionToken: SessionToken)
                          (implicit state: PasswordResetModelState):
  CommandPartialValidation[SessionToken] =
    if (!sessionToken.token.isEmpty) {
      \/-(sessionToken)
    } else {
      -\/(invalidSessionToken.code)
    }

  def validateTokenPair(sessionToken: SessionToken,
                        passwordResetToken: PasswordResetToken)
                       (implicit state: PasswordResetModelState):
  CommandPartialValidation[PasswordResetToken] =
    if (sideEffects.validatePasswordResetToken(sessionToken, passwordResetToken)) {
      \/-(passwordResetToken)
    } else {
      -\/(invalidTokenPair.code)
    }

  def validatePassword(newPassword: UserPassword)
                      (implicit state: PasswordResetModelState):
  CommandPartialValidation[UserPassword] =
    if (!newPassword.passwordHash.isEmpty) {
      // TODO common password validation must be defined
      \/-(newPassword)
    } else {
      -\/(invalidPassword.code)
    }

}
