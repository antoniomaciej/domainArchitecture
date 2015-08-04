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

package eu.pmsoft.mcomponents.model.user.session.mins

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel.CommandResultConfirmed
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.model.user.registry.mins._
import eu.pmsoft.mcomponents.model.user.session._
import eu.pmsoft.mcomponents.reqres.ReqResDataModel

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserServiceComponentInstanceTest extends ComponentSpec {

  import ReqResDataModel._
  import UserSessionApplicationDefinitions._

  it should "return a critical error if user is not found after successful registration command" in {
    val userSessionStateMock = new OrderedEventStoreProjectionView[UserSessionSSOState] {
      override def atLeastOn(storeVersion: EventStoreVersion): Future[UserSessionSSOState] =
        Future.successful(new UserSessionSSOState {
          override def findAllUserSessions(): Stream[UserSession] = Mocked.shouldNotBeCalled

          override def findUserSession(userId: UserID): Option[UserSession] = None

          override def findUserSession(sessionToken: SessionToken): Option[UserSession] = Mocked.shouldNotBeCalled
        })
    }

    val commandHandler = new AsyncEventCommandHandler[UserSessionCommand] {
      override def execute(command: UserSessionCommand): Future[CommandResultConfirmed] =
        Future.successful(
          scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion(0L)))
        )
    }
    val userRegistrationMock = new UserRegistrationApi {
      override def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]] =
        Future.successful(scalaz.\/-(SearchForUserIdResponse(UserID(0L))))

      override def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]] = Mocked.shouldNotBeCalled
    }

    val dispatcher = new UserServiceComponentInstance(userRegistrationMock, commandHandler, userSessionStateMock)
    val result = dispatcher.loginUser(UserLoginRequest(UserLogin("any"), UserPassword("any"))).futureValue
    result should be_-\/(UserSessionModel.criticalSessionNotFoundAfterSuccessCommand.toResponseError)

  }

}

