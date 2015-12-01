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

package eu.pmsoft.mcomponents.model.user.session.api

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.CommandResultConfirmed
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import eu.pmsoft.mcomponents.minstance.ReqResDataModel
import eu.pmsoft.mcomponents.model.user.registry.api._
import eu.pmsoft.mcomponents.model.user.session._
import eu.pmsoft.mcomponents.test.{ BaseEventSourceComponentTestSpec, Mocked }

import scala.concurrent.Future

import ReqResDataModel._
import UserSessionApi._
class UserServiceDispatcherTest extends BaseEventSourceComponentTestSpec {

  implicit val executionContext = scala.concurrent.ExecutionContext.global
  it should "return a critical error if user is not found after successful registration command" in {
    //given
    val userRegistrationMock = new UserRegistrationApi {
      override def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]] =
        Future.successful(scalaz.\/-(SearchForUserIdResponse(UserID(0L))))

      override def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]] = Mocked.shouldNotBeCalled
    }
    val dispatcher = new UserServiceDispatcher(userRegistrationMock, new TestCase())
    //when
    val result = dispatcher.loginUser(UserLoginRequest(UserLogin("any"), UserPassword("any"))).futureValue
    //then
    result should be_-\/(UserSessionModel.criticalSessionNotFoundAfterSuccessCommand.toResponseError)

  }

  class TestCase extends DomainCommandApi[UserSessionSSODomain] {
    override implicit def eventSourcingConfiguration: EventSourcingConfiguration = EventSourcingConfiguration(executionContext, LocalBindingInfrastructure.create(), Set())

    override def commandHandler: AsyncEventCommandHandler[UserSessionSSODomain] = new AsyncEventCommandHandler[UserSessionSSODomain] {
      override def execute(command: UserSessionCommand): Future[CommandResultConfirmed] =
        Future.successful(
          scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion(0L)))
        )
    }

    override def atomicProjection: VersionedEventStoreView[UserSessionAggregate, UserSessionSSOState] =
      new VersionedEventStoreView[UserSessionAggregate, UserSessionSSOState] {

        override def atLeastOn(storeVersion: EventStoreVersion): Future[VersionedProjection[UserSessionSSOState]] =
          Future.successful(VersionedProjection(storeVersion, new UserSessionSSOState {
            override def findAllUserSessions(): Stream[UserSession] = Mocked.shouldNotBeCalled

            override def findUserSession(userId: UserID): Option[UserSession] = None // <<<<<<-----

            override def findUserSession(sessionToken: SessionToken): Option[UserSession] = Mocked.shouldNotBeCalled
          }))

        override def lastSnapshot(): UserSessionSSOState = Mocked.shouldNotBeCalled
      }
  }

}

