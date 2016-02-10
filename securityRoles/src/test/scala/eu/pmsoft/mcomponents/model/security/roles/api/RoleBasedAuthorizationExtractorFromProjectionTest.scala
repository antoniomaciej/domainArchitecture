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

package eu.pmsoft.mcomponents.model.security.roles.api

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.CommandResultConfirmed
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import eu.pmsoft.mcomponents.minstance.ReqResDataModel
import eu.pmsoft.mcomponents.model.security.roles._
import eu.pmsoft.mcomponents.test.{ BaseEventSourceComponentTestSpec, Mocked }
import org.scalamock.scalatest.MockFactory

import scala.concurrent.{ ExecutionContext, Future }

class RoleBasedAuthorizationExtractorFromProjectionTest extends BaseEventSourceComponentTestSpec with MockFactory {

  import ReqResDataModel._
  import RoleBasedAuthorizationApi._

  it should "failure with permissionNotFoundAfterInsert when permission not found after insert" in {

    // TODO find a good mocking substitute of nested Mocked.shouldNotBeCalled
    //    val stateMock = stub[RoleBasedAuthorizationState]
    //    (stateMock.permissionByCode _).when("abyCode").returns(None)
    //    val projectionMock = stub[VersionedEventStoreView[RoleBasedAuthorizationAggregate, RoleBasedAuthorizationState]]
    //    (projectionMock.lastSnapshot _).when().returns(Future.successful(stateMock))
    //
    //    val cmdApiMock = stub[DomainCommandApi[RoleBasedAuthorizationDomain]]
    //    (cmdApiMock.atomicProjection _).when().returns(projectionMock)
    //    val mocked = stub[RoleBasedAuthorizationExtractorFromProjection]
    //    (mocked.commandApi _).when().returns(cmdApiMock)

    val mocked: RoleBasedAuthorizationExtractorFromProjection = createMocked

    val notFoundExpected = mocked.findPermissionByName(EventSourceCommandConfirmation(EventStoreVersion.zero, RoleIdAggregate(RoleID(0L))), "anyCode").futureValue
    notFoundExpected shouldBe -\/
    notFoundExpected shouldBe scalaz.-\/(RoleBasedAuthorizationRequestModel.permissionNotFoundAfterInsert.toResponseError)

  }
  it should "failure with roleNotFoundAfterInsert when role not found after insert" in {

    val mocked: RoleBasedAuthorizationExtractorFromProjection = createMocked
    val notFoundExpected = mocked.findRoleByName(EventSourceCommandConfirmation(EventStoreVersion.zero, RoleIdAggregate(RoleID(0L))), "anyName").futureValue
    notFoundExpected shouldBe -\/
    notFoundExpected shouldBe scalaz.-\/(RoleBasedAuthorizationRequestModel.roleNotFoundAfterInsert.toResponseError)

  }

  private def createMocked: RoleBasedAuthorizationExtractorFromProjection = new MockedRoleBasedAuthorizationExtractorFromProjection()
}

class MockedRoleBasedAuthorizationExtractorFromProjection extends RoleBasedAuthorizationExtractorFromProjection with EventSourcingConfigurationContext with ExecutionContextFromConfiguration {

  override implicit def eventSourcingConfiguration: EventSourcingConfiguration = EventSourcingConfiguration(ExecutionContext.Implicits.global, LocalBindingInfrastructure.create(), Set())

  override def commandApi: DomainCommandApi[RoleBasedAuthorizationDomain] = new DomainCommandApi[RoleBasedAuthorizationDomain] {

    override implicit def eventSourcingConfiguration: EventSourcingConfiguration = EventSourcingConfiguration(ExecutionContext.Implicits.global, LocalBindingInfrastructure.create(), Set())

    override def commandHandler: AsyncEventCommandHandler[RoleBasedAuthorizationDomain] = new AsyncEventCommandHandler[RoleBasedAuthorizationDomain] {
      override def execute(command: RoleBasedAuthorizationModelCommand): Future[CommandResultConfirmed[RoleBasedAuthorizationDomain#Aggregate]] = Mocked.shouldNotBeCalled
    }

    override def atomicProjection: VersionedEventStoreView[RoleBasedAuthorizationState] =
      new VersionedEventStoreView[RoleBasedAuthorizationState] {

        override def lastSnapshot(): RoleBasedAuthorizationState = Mocked.shouldNotBeCalled

        override def atLeastOn(storeVersion: EventStoreVersion): Future[VersionedProjection[RoleBasedAuthorizationState]] =
          Future.successful(VersionedProjection(EventStoreVersion.zero, new RoleBasedAuthorizationState {
            //Roles
            override def allRoleId: Stream[RoleID] = Mocked.shouldNotBeCalled

            override def roleIdExists(roleId: RoleID): Boolean = Mocked.shouldNotBeCalled

            // Permission X Role relation
            override def isPermissionInRole(roleId: RoleID, permissionID: PermissionID): Boolean = Mocked.shouldNotBeCalled

            override def getPermissionsForRole(roleId: RoleID): Set[PermissionID] = Mocked.shouldNotBeCalled

            //Permissions
            override def allPermissionID: Stream[PermissionID] = Mocked.shouldNotBeCalled

            override def roleByName(roleName: String): Option[AccessRole] = None

            override def roleById(roleId: RoleID): Option[AccessRole] = Mocked.shouldNotBeCalled

            override def permissionIdExists(permissionID: PermissionID): Boolean = Mocked.shouldNotBeCalled

            override def permissionByCode(code: String): Option[Permission] = None

            override def permissionById(permissionID: PermissionID): Option[Permission] = Mocked.shouldNotBeCalled
          }))
      }

  }

}
