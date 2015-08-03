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

package eu.pmsoft.mcomponents.model.security.roles.mins

import eu.pmsoft.domain.model.ComponentSpec
import eu.pmsoft.domain.model.security.roles.mins._
import eu.pmsoft.mcomponents.minstance.{ApiContract, MicroComponentRegistry}
import eu.pmsoft.mcomponents.model.security.roles._
import eu.pmsoft.mcomponents.model.security.roles.inmemory.RoleBasedAuthorizationInMemoryApplication

import scala.concurrent.ExecutionContext.Implicits.global

class RoleBasedAuthorizationComponentTest extends ComponentSpec {

  it should "extract roles to permissions map" in {
    val api = createComponent()
    val roleID = api.createRole(CreateRoleRequest("test")).futureValue.toOption.get.roleID
    val p1 = api.createPermission(CreatePermissionRequest("code1", "description1")).futureValue.toOption.get.permissionId
    val p2 = api.createPermission(CreatePermissionRequest("code2", "description2")).futureValue.toOption.get.permissionId

    api.addPermissionToRole(AddPermissionsToRoleRequest(roleID, Set(p1, p2))).futureValue shouldBe \/-
    checkRoleToPermission(Map(roleID -> Set(p1, p2)))
    api.deletePermissionFromRole(DeletePermissionsFromRoleRequest(roleID, Set(p1))).futureValue shouldBe \/-
    checkRoleToPermission(Map(roleID -> Set(p2)))

    def checkRoleToPermission(expected: Map[RoleID, Set[PermissionID]]): Unit = {
      val roleToPermissionRes = api.getRolesPermissions(GetRolesPermissionsRequest(Set(roleID))).futureValue
      roleToPermissionRes shouldBe \/-
      val roleToPermission = roleToPermissionRes.toOption.get
      roleToPermission.permissions should contain theSameElementsAs expected
    }

  }
  it should "create roles" in {
    val api = createComponent()
    api.createRole(CreateRoleRequest("test")).futureValue shouldBe \/-
  }
  it should "delete roles" in {
    val api = createComponent()
    val created = api.createRole(CreateRoleRequest("test")).futureValue
    api.deleteRole(DeleteRoleRequest(created.toOption.get.roleID)).futureValue shouldBe \/-
  }

  it should "create roles, load all, update and check" in {
    val api = createComponent()
    api.createRole(CreateRoleRequest("test1")).futureValue shouldBe \/-
    api.createRole(CreateRoleRequest("test2")).futureValue shouldBe \/-
    api.createRole(CreateRoleRequest("test3")).futureValue shouldBe \/-

    def loadAndCheck(expectedRoleNames: Set[String]): Set[AccessRole] = {
      val loaded = api.getRoles(GetRolesRequest()).futureValue
      loaded shouldBe \/-
      val roles = loaded.toOption.get.roles
      val rolesNames = roles.map(_.roleName)
      rolesNames should contain theSameElementsAs expectedRoleNames
      roles
    }
    val roles = loadAndCheck(Set("test1", "test2", "test3"))
    val selected = roles.find(_.roleName == "test1").get

    api.updateRole(UpdateRoleNameRequest(selected.roleId, "changed")).futureValue shouldBe \/-
    loadAndCheck(Set("changed", "test2", "test3"))

    api.deleteRole(DeleteRoleRequest(selected.roleId)).futureValue shouldBe \/-
    loadAndCheck(Set("test2", "test3"))


  }

  it should "create permission" in {
    val api = createComponent()
    api.createPermission(CreatePermissionRequest("code", "description")).futureValue shouldBe \/-
  }

  it should "create permission, load all, update and check" in {
    val api = createComponent()
    api.createPermission(CreatePermissionRequest("code1", "description1")).futureValue shouldBe \/-
    api.createPermission(CreatePermissionRequest("code2", "description2")).futureValue shouldBe \/-
    api.createPermission(CreatePermissionRequest("code3", "description3")).futureValue shouldBe \/-

    def loadAndCheck(expectedPermissions: Set[(String, String)]): Set[Permission] = {
      val loaded = api.getPermissions(GetPermissionsRequest()).futureValue
      loaded shouldBe \/-
      val permissions = loaded.toOption.get.permissions
      permissions.map(p => (p.code, p.description)) should contain theSameElementsAs expectedPermissions
      permissions
    }
    val permissions = loadAndCheck(Set(("code1", "description1"), ("code2", "description2"), ("code3", "description3")))

    val selected = permissions.find(_.code == "code1").get

    api.updatePermission(UpdatePermissionDescriptionRequest(selected.permissionId, "changedDescription")).futureValue shouldBe \/-
    loadAndCheck(Set(("code1", "changedDescription"), ("code2", "description2"), ("code3", "description3")))

    api.deletePermission(DeletePermissionRequest(selected.permissionId)).futureValue shouldBe \/-
    loadAndCheck(Set(("code2", "description2"), ("code3", "description3")))

  }

  def createComponent(): RoleBasedAuthorizationApi = {
    val registry = MicroComponentRegistry.create()

    val roleAuth = new RoleBasedAuthorizationComponent {
      override lazy val application: RoleBasedAuthorizationApplication = new RoleBasedAuthorizationInMemoryApplication()
    }

    registry.registerComponent(roleAuth)
    registry.initializeInstances().futureValue shouldBe \/-
    registry.bindComponent(
      ApiContract(classOf[RoleBasedAuthorizationApi])
    ).futureValue
  }
}