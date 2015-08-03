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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry

import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel._
import eu.pmsoft.mcomponents.eventsourcing.{CommandToTransactionScope, DomainLogic, EventSourceCommandFailed}
import org.apache.commons.validator.routines.EmailValidator

import scalaz._

trait TestUserRegistrationModule {

}


/**
 * Projection that should be changes atomically with relation to the handled commands.
 */
trait TestUserRegistrationState {
  def uidExists(uid: TestUserID): Boolean

  def getAllUid: Stream[TestUserID]

  def getUserByID(uid: TestUserID): Option[TestUser]

  def getUserByLogin(login: TestUserLogin): Option[TestUser]

  def loginExists(login: TestUserLogin): Boolean

}

trait TestUserRegistrationLocalSideEffects {
  def createNextUid(): TestUserID
}

final class TestUserRegistrationCommandToTransactionScope
  extends CommandToTransactionScope[TestUserRegistrationCommand, TestUserRegistrationAggregate, TestUserRegistrationState] {
  override def calculateTransactionScope(command: TestUserRegistrationCommand, state: TestUserRegistrationState):
  CommandToAggregateResult[TestUserRegistrationAggregate] =
    command match {
      case TestAddUser(loginEmail, passwordHash) => \/-(Set(EmailAggregateIdTest(loginEmail)))
      case TestUpdateUserPassword(uid, passwordHash) => \/-(Set(TestUserAggregateId(uid)))
      case TestUpdateActiveUserStatus(uid, active) => \/-(Set(TestUserAggregateId(uid)))
      case TestUpdateUserRoles(uid, roles) => \/-(Set(TestUserAggregateId(uid)))
    }

}

final class TestTestUserRegistrationHandlerLogic(val sideEffects: TestUserRegistrationLocalSideEffects) extends
DomainLogic[TestUserRegistrationCommand, TestUserRegistrationEvent, TestUserRegistrationAggregate, TestUserRegistrationState] with
TestUserRegistrationValidations {

  override def executeCommand(command: TestUserRegistrationCommand,
                              transactionScope: Map[TestUserRegistrationAggregate, Long])
                             (implicit state: TestUserRegistrationState):
  CommandToEventsResult[TestUserRegistrationEvent] = command match {
    case TestUpdateActiveUserStatus(uid, active) => for {
      user <- validUidExtractUser(uid)
    } yield if (user.activeStatus == active) {
        //Do not activate if status match the state
        List()
      } else {
        List(TestUserActiveStatusUpdated(uid, active))
      }

    case TestAddUser(loginEmail, passwordHash) => for {
      login <- availableLogin(loginEmail)
      email <- validEmail(loginEmail.login)
    } yield List(TestUserCreated(sideEffects.createNextUid(), loginEmail, passwordHash))

    case TestUpdateUserPassword(uid, passwordHash) => for {
      user <- validUidExtractUser(uid)
    } yield List(TestUserPasswordUpdated(uid, passwordHash))

    case TestUpdateUserRoles(uid, roles) => for {
      user <- validUidExtractUser(uid)
    } yield List(TestUserObtainedAccessRoles(uid, roles))
  }

}

import eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry.TestUserRegistrationModel._

sealed trait TestUserRegistrationValidations {

  def availableLogin(login: TestUserLogin)(implicit atomicState: TestUserRegistrationState)
  : CommandPartialValidation[TestUserLogin] =
    if (!atomicState.loginExists(login)) {
      \/-(login)
    } else {
      -\/(EventSourceCommandFailed(invalidLoginTest.code))
    }

  def validEmail(email: String)(implicit atomicState: TestUserRegistrationState)
  : CommandPartialValidation[String] =
    if (EmailValidator.getInstance().isValid(email)) {
      \/-(email)
    } else {
      -\/(EventSourceCommandFailed(invalidEmailTest.code))
    }

  def validUidExtractUser(uid: TestUserID)(implicit atomicState: TestUserRegistrationState)
  : CommandPartialValidation[TestUser] = atomicState.getUserByID(uid) match {
    case Some(x) => \/-(x)
    case None => -\/(EventSourceCommandFailed(notExistingUserIDTest.code))
  }
}
