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

package eu.pmsoft.mcomponents.model.user.registry.mins

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel.CommandResultConfirmed
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.model.user.registry._
import eu.pmsoft.mcomponents.reqres.ReqResDataModel

import scala.concurrent.{ExecutionContext, Future}

class UserRegistrationRequestDispatcherTest extends ComponentSpec {

  import ReqResDataModel._
  import UserRegistrationApplicationDefinitions._

  it should "return a critical error if user is not found after successful registration command" in {
    val registrationStateMock = new AtomicEventStoreProjectionView[UserRegistrationState] {
      override def atLeastOn(storeVersion: EventStoreVersion): Future[UserRegistrationState] =
        Future.successful(new UserRegistrationState {
          override def uidExists(uid: UserID): Boolean = Mocked.shouldNotBeCalled

          override def getUserByLogin(login: UserLogin): Option[User] = None

          override def getAllUid: Stream[UserID] = Mocked.shouldNotBeCalled

          override def getUserByID(uid: UserID): Option[User] = Mocked.shouldNotBeCalled

          override def loginExists(login: UserLogin): Boolean = Mocked.shouldNotBeCalled
        })

      override def lastSnapshot(): Future[UserRegistrationState] = Mocked.shouldNotBeCalled
    }

    val commandHandler = new AsyncEventCommandHandler[UserRegistrationCommand] {
      override def execute(command: UserRegistrationCommand): Future[CommandResultConfirmed] =
        Future.successful(
          scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion(0L)))
        )
    }

    val dispatcher = new UserRegistrationRequestDispatcher(registrationStateMock, commandHandler)(ExecutionContext.global)
    val result = dispatcher.registerUser(RegisterUserRequest(UserLogin("any"), UserPassword("any"))).futureValue
    result should be_-\/(UserRegistrationRequestModel.criticalUserNotFoundAfterSuccessRegistration.toResponseError)
  }

}
