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

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing._

import scala.pickling.directSubclasses

object PasswordResetModel {

  val invalidSessionTokenErrorCode = 100L
  val invalidSessionToken = EventSourceModelError(
    "invalid session token",
    EventSourceCommandError(invalidSessionTokenErrorCode)
  )

  val invalidTokenPairErrorCode = 101L
  val invalidTokenPair = EventSourceModelError(
    "invalid tokens pair",
    EventSourceCommandError(invalidTokenPairErrorCode)
  )

  val invalidPasswordErrorCode = 102L
  val invalidPassword = EventSourceModelError(
    "invalid password",
    EventSourceCommandError(invalidPasswordErrorCode)
  )

  val invalidPasswordResetTokenErrorCode = 103L
  val invalidPasswordResetToken = EventSourceModelError(
    "invalid password reset token",
    EventSourceCommandError(invalidPasswordResetTokenErrorCode)
  )

  val notFoundPasswordResetTokenErrorCode = 104L
  val notFoundPasswordResetToken = EventSourceModelError(
    "password token for usedId not found during process cancellation",
    EventSourceCommandError(notFoundPasswordResetTokenErrorCode)
  )

  val criticalUserIdNotFoundInTransactionScopeErrorCode = 110L
  val criticalUserIdNotFoundInTransactionScope = EventSourceModelError(
    "Critical: UserID can not by found in transaction scope",
    EventSourceCommandError(criticalUserIdNotFoundInTransactionScopeErrorCode)
  )

  val criticalTwoUserIdInTransactionScopeErrorCode = 111L
  val criticalTwoUserIdInTransactionScope = EventSourceModelError(
    "Critical: UserID can not by found in transaction scope",
    EventSourceCommandError(criticalTwoUserIdInTransactionScopeErrorCode)
  )

}

//model
case class PasswordResetFlowStatus(
  userId:             UserID,
  sessionToken:       SessionToken,
  passwordResetToken: PasswordResetToken
)

case class PasswordResetToken(val token: String) extends AnyVal

//aggregates
sealed trait PasswordResetAggregate

case class UserIdFlowAggregate(userID: UserID) extends PasswordResetAggregate

//commands
sealed trait PasswordResetModelCommand

case class InitializePasswordResetFlow(
  userId:       UserID,
  sessionToken: SessionToken
) extends PasswordResetModelCommand

case class CancelPasswordResetFlowByUser(userId: UserID) extends PasswordResetModelCommand

case class CancelPasswordResetFlowByToken(passwordResetToken: PasswordResetToken) extends PasswordResetModelCommand

case class ConfirmPasswordResetFlow(
  sessionToken:       SessionToken,
  passwordResetToken: PasswordResetToken,
  newPassword:        UserPassword
) extends PasswordResetModelCommand

//events
@directSubclasses(Array(
  classOf[PasswordResetFlowCreated],
  classOf[PasswordResetFlowCancelled],
  classOf[PasswordResetFlowConfirmed]
))
sealed trait PasswordResetModelEvent

case class PasswordResetFlowCreated(
  userId:             UserID,
  sessionToken:       SessionToken,
  passwordResetToken: PasswordResetToken
) extends PasswordResetModelEvent

case class PasswordResetFlowCancelled(
  userId:             UserID,
  passwordResetToken: PasswordResetToken
) extends PasswordResetModelEvent

case class PasswordResetFlowConfirmed(
  userId:      UserID,
  newPassword: UserPassword
) extends PasswordResetModelEvent

