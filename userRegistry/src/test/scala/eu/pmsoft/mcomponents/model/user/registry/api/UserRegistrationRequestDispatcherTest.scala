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

package eu.pmsoft.mcomponents.model.user.registry.api

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.CommandResultConfirmed
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import eu.pmsoft.mcomponents.minstance.ReqResDataModel._
import eu.pmsoft.mcomponents.model.user.registry._
import eu.pmsoft.mcomponents.model.user.registry.api.UserRegistrationApi._
import eu.pmsoft.mcomponents.test.{ BaseEventSourceComponentTestSpec, Mocked }

import scala.concurrent.{ ExecutionContext, Future }

class UserRegistrationRequestDispatcherTest extends BaseEventSourceComponentTestSpec {

  implicit val executionContext = ExecutionContext.Implicits.global

  it should "return a critical error if user is not found after successful registration command" in {
    val dispatcher = new UserRegistrationRequestDispatcher(new TestCase())
    val result = dispatcher.registerUser(RegisterUserRequest(UserLogin("any"), UserPassword("any"))).futureValue
    result should be_-\/(UserRegistrationRequestModel.criticalUserNotFoundAfterSuccessRegistration.toResponseError)
  }

  class TestCase extends DomainCommandApi[UserRegistrationDomain] {

    override implicit def eventSourcingConfiguration: EventSourcingConfiguration = EventSourcingConfiguration(executionContext, LocalBindingInfrastructure.create(), Set())

    override def commandHandler: AsyncEventCommandHandler[UserRegistrationDomain] = new AsyncEventCommandHandler[UserRegistrationDomain] {
      override def execute(command: UserRegistrationCommand): Future[CommandResultConfirmed[UserRegistrationDomain#Aggregate]] =
        Future.successful(
          scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion.zero, UserAggregateId(UserID(0))))
        )
    }

    override def atomicProjection: VersionedEventStoreView[UserRegistrationAggregate, UserRegistrationState] =
      new VersionedEventStoreView[UserRegistrationAggregate, UserRegistrationState] {

        override def atLeastOn(storeVersion: EventStoreVersion): Future[VersionedProjection[UserRegistrationState]] =
          Future.successful(VersionedProjection(storeVersion, new UserRegistrationState {
            override def uidExists(uid: UserID): Boolean = Mocked.shouldNotBeCalled

            override def getUserByLogin(login: UserLogin): Option[User] = None

            override def getAllUid: Stream[UserID] = Mocked.shouldNotBeCalled

            override def getUserByID(uid: UserID): Option[User] = Mocked.shouldNotBeCalled

            override def loginExists(login: UserLogin): Boolean = Mocked.shouldNotBeCalled
          }))

        override def lastSnapshot(): UserRegistrationState = Mocked.shouldNotBeCalled
      }
  }

}
