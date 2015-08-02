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

package eu.pmsoft.domain.model.user.registry

import eu.pmsoft.mcomponents.eventsourcing.{CommandToTransactionScope, DomainLogic, EventSourceCommandFailed, EventSourceDataModel}
import EventSourceDataModel._
import org.apache.commons.validator.routines.EmailValidator

import scalaz._

trait UserRegistrationModule {

}


/**
 * Projection that should be changes atomically with relation to the handled commands.
 */
trait UserRegistrationState {
  def uidExists(uid: UserID): Boolean

  def getAllUid: Stream[UserID]

  def getUserByID(uid: UserID): Option[User]

  def getUserByLogin(login: UserLogin): Option[User]

  def loginExists(login: UserLogin): Boolean

}

trait UserRegistrationLocalSideEffects {
  def createNextUid(): UserID
}

final class UserRegistrationCommandToTransactionScope
  extends CommandToTransactionScope[UserRegistrationCommand, UserRegistrationAggregate, UserRegistrationState] {
  override def calculateTransactionScope(command: UserRegistrationCommand, state: UserRegistrationState):
  CommandToAggregateResult[UserRegistrationAggregate] =
    command match {
      case AddUser(loginEmail, passwordHash) => \/-(Set(EmailAggregateId(loginEmail)))
      case UpdateUserPassword(uid, passwordHash) => \/-(Set(UserAggregateId(uid)))
      case UpdateActiveUserStatus(uid, active) => \/-(Set(UserAggregateId(uid)))
      case UpdateUserRoles(uid, roles) => \/-(Set(UserAggregateId(uid)))
    }

}

final class UserRegistrationHandlerLogic(val sideEffects: UserRegistrationLocalSideEffects) extends
DomainLogic[UserRegistrationCommand, UserRegistrationEvent, UserRegistrationAggregate, UserRegistrationState] with
UserRegistrationValidations {

  override def executeCommand(command: UserRegistrationCommand,
                              transactionScope: Map[UserRegistrationAggregate, Long])
                             (implicit state: UserRegistrationState):
  CommandToEventsResult[UserRegistrationEvent] = command match {
    case UpdateActiveUserStatus(uid, active) => for {
      user <- validUidExtractUser(uid)
    } yield if (user.activeStatus == active) {
        //Do not activate if status match the state
        List()
      } else {
        List(UserActiveStatusUpdated(uid, active))
      }

    case AddUser(loginEmail, passwordHash) => for {
      login <- availableLogin(loginEmail)
      email <- validEmail(loginEmail.login)
    } yield List(UserCreated(sideEffects.createNextUid(), loginEmail, passwordHash))

    case UpdateUserPassword(uid, passwordHash) => for {
      user <- validUidExtractUser(uid)
    } yield List(UserPasswordUpdated(uid, passwordHash))

    case UpdateUserRoles(uid, roles) => for {
      user <- validUidExtractUser(uid)
    } yield List(UserObtainedAccessRoles(uid, roles))
  }

}

import UserRegistrationModel._
sealed trait UserRegistrationValidations {

  def availableLogin(login: UserLogin)(implicit atomicState: UserRegistrationState): CommandPartialValidation[UserLogin] =
    if (!atomicState.loginExists(login)) {
      \/-(login)
    } else {
      -\/(EventSourceCommandFailed(invalidLogin.code))
    }

  def validEmail(email: String)(implicit atomicState: UserRegistrationState): CommandPartialValidation[String] =
    if (EmailValidator.getInstance().isValid(email)) {
      \/-(email)
    } else {
      -\/(EventSourceCommandFailed(invalidEmail.code))
    }

  def validUidExtractUser(uid: UserID)(implicit atomicState: UserRegistrationState): CommandPartialValidation[User] = atomicState.getUserByID(uid) match {
    case Some(x) => \/-(x)
    case None => -\/(EventSourceCommandFailed(notExistingUserID.code))
  }
}
