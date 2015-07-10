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

package eu.pmsoft.domain.model.security.roles.mins

import eu.pmsoft.domain.model.security.roles._
import eu.pmsoft.domain.model.{AtomicEventStoreProjection, ComponentSpec, EventSourceCommandConfirmation, EventStoreVersion}
import eu.pmsoft.domain.test.util.Mocked

import scala.concurrent.{ExecutionContext, Future}

class RoleBasedAuthorizationExtractorFromProjectionTest extends ComponentSpec {

  import eu.pmsoft.domain.model.EventSourceDataModel._
  import eu.pmsoft.domain.model.security.roles.mins.RoleBasedAuthorizationDefinitions._

  it should "failure with permissionNotFoundAfterInsert when permission not found after insert" in {

    val mocked = createMocked
    val notFoundExpected = mocked.findPermissionByName(EventSourceCommandConfirmation(EventStoreVersion(0L)), "anyCode").futureValue
    notFoundExpected shouldBe -\/
    notFoundExpected shouldBe scalaz.-\/(RoleBasedAuthorizationRequestModel.permissionNotFoundAfterInsert.toResponseError)

  }
  it should "failure with roleNotFoundAfterInsert when role not found after insert" in {

    val mocked = createMocked
    val notFoundExpected = mocked.findRoleByName(EventSourceCommandConfirmation(EventStoreVersion(0L)), "anyName").futureValue
    notFoundExpected shouldBe -\/
    notFoundExpected shouldBe scalaz.-\/(RoleBasedAuthorizationRequestModel.roleNotFoundAfterInsert.toResponseError)

  }

  private def createMocked = new RoleBasedAuthorizationExtractorFromProjection {

    override implicit def executionContext: ExecutionContext = ExecutionContext.global

    override def projection: AtomicEventStoreProjection[RoleBasedAuthorizationState] =
      new AtomicEventStoreProjection[RoleBasedAuthorizationState] {

        override def lastSnapshot(): Future[RoleBasedAuthorizationState] = Mocked.shouldNotBeCalled

        override def atLeastOn(storeVersion: EventStoreVersion): Future[RoleBasedAuthorizationState] =
          Future.successful(new RoleBasedAuthorizationState {
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
          })
      }
  }
}
