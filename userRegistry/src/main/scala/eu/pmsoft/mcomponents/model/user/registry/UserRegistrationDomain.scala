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

package eu.pmsoft.mcomponents.model.user.registry

import eu.pmsoft.domain.model.{ UserID, UserLogin }
import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import org.apache.commons.validator.routines.EmailValidator

import scalaz._

final class UserRegistrationDomain extends DomainSpecification {
  type Command = UserRegistrationCommand
  type Event = UserRegistrationEvent
  type Aggregate = UserRegistrationAggregate
  type State = UserRegistrationState
  type SideEffects = UserRegistrationLocalSideEffects
}

/** Projection that should be changes atomically with relation to the handled commands.
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

final class UserRegistrationEventSerializationSchema extends EventSerializationSchema[UserRegistrationDomain] {
  import scala.pickling.static._
  import scala.pickling.Defaults._
  import scala.pickling.binary._
  override def mapToEvent(data: EventDataWithNr): UserRegistrationEvent = {
    data.eventBytes.unpickle[UserRegistrationEvent]
  }

  override def buildReference(aggregate: UserRegistrationAggregate): AggregateReference = aggregate match {
    case UserAggregateId(uid)         => AggregateReference(0, uid.id)
    case EmailAggregateId(loginEmail) => AggregateReference(1, loginEmail.login)
  }

  override def eventToData(event: UserRegistrationEvent): EventData = {
    EventData(event.pickle.value)
  }

}

final class UserRegistrationHandlerLogic extends DomainLogic[UserRegistrationDomain] with UserRegistrationValidations {

  override def calculateTransactionScope(command: UserRegistrationCommand, state: UserRegistrationState): CommandToAggregates[UserRegistrationDomain] =
    command match {
      case AddUser(loginEmail, passwordHash)     => \/-(Set(EmailAggregateId(loginEmail)))
      case UpdateUserPassword(uid, passwordHash) => \/-(Set(UserAggregateId(uid)))
      case UpdateActiveUserStatus(uid, active)   => \/-(Set(UserAggregateId(uid)))
      case UpdateUserRoles(uid, roles)           => \/-(Set(UserAggregateId(uid)))
    }

  override def executeCommand(
    command:          UserRegistrationCommand,
    transactionScope: Map[UserRegistrationAggregate, Long]
  )(implicit state: UserRegistrationState, sideEffects: UserRegistrationLocalSideEffects): CommandToEventsResult[UserRegistrationDomain] = command match {
    case UpdateActiveUserStatus(uid, active) => for {
      user <- validUidExtractUser(uid)
    } yield if (user.activeStatus == active) {
      //Do not activate if status match the state
      CommandModelResult(List(), UserAggregateId(uid))
    }
    else {
      CommandModelResult(List(UserActiveStatusUpdated(uid, active)), UserAggregateId(uid))
    }

    case AddUser(loginEmail, passwordHash) => for {
      login <- availableLogin(loginEmail)
      email <- validEmail(loginEmail.login)
    } yield {
      val uid = sideEffects.createNextUid()
      CommandModelResult(List(UserCreated(uid, loginEmail, passwordHash)), UserAggregateId(uid))
    }

    case UpdateUserPassword(uid, passwordHash) => for {
      user <- validUidExtractUser(uid)
    } yield CommandModelResult(List(UserPasswordUpdated(uid, passwordHash)), UserAggregateId(uid))

    case UpdateUserRoles(uid, roles) => for {
      user <- validUidExtractUser(uid)
    } yield CommandModelResult(List(UserObtainedAccessRoles(uid, roles)), UserAggregateId(uid))
  }

}

import eu.pmsoft.mcomponents.model.user.registry.UserRegistrationModel._

sealed trait UserRegistrationValidations {

  def availableLogin(login: UserLogin)(implicit atomicState: UserRegistrationState): CommandPartialValidation[UserLogin] =
    if (!atomicState.loginExists(login)) {
      \/-(login)
    }
    else {
      -\/(EventSourceCommandFailed(invalidLogin.code))
    }

  def validEmail(email: String)(implicit atomicState: UserRegistrationState): CommandPartialValidation[String] =
    if (EmailValidator.getInstance().isValid(email)) {
      \/-(email)
    }
    else {
      -\/(EventSourceCommandFailed(invalidEmail.code))
    }

  def validUidExtractUser(uid: UserID)(implicit atomicState: UserRegistrationState): CommandPartialValidation[User] = atomicState.getUserByID(uid) match {
    case Some(x) => \/-(x)
    case None    => -\/(EventSourceCommandFailed(notExistingUserID.code))
  }
}
