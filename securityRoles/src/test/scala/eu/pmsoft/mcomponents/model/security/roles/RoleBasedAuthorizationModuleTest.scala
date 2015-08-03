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

package eu.pmsoft.mcomponents.model.security.roles

import eu.pmsoft.domain.model.{BaseEventSourceSpec, CommandGenerator, GeneratedCommandSpecification}
import eu.pmsoft.mcomponents.eventsourcing.AtomicEventStoreProjection

abstract class RoleBasedAuthorizationModuleTest[M] extends BaseEventSourceSpec with
GeneratedCommandSpecification[RoleBasedAuthorizationModelCommand, RoleBasedAuthorizationEvent, RoleBasedAuthorizationState, M] {

  it should "not allow empty roles names" in {
    val module = createEmptyModule()
    asyncCommandHandler(module).execute(CreateRole("")).futureValue should be(-\/)
  }
  it should "not allow duplicated roles names" in {
    val module = createEmptyModule()
    asyncCommandHandler(module).execute(CreateRole("correct")).futureValue should be(\/-)
    asyncCommandHandler(module).execute(CreateRole("correct")).futureValue should be(-\/)
  }
  it should "fail when adding not existing permissions to role" in {
    val module = createEmptyModule()
    asyncCommandHandler(module).execute(CreateRole("test")).futureValue shouldBe \/-
    val roleId = stateProjection(module).lastSnapshot().futureValue.allRoleId.head
    asyncCommandHandler(module).execute(AddPermissionsToRole(Set(PermissionID(0L)), roleId)).futureValue shouldBe -\/

  }
  it should "not allow empty permission descriptions" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(CreatePermission("name", ""))) { result =>
      result should be(-\/)
    }
  }
  it should "not allow empty permission names" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(CreatePermission("", "description"))) { result =>
      result should be(-\/)
    }
  }
  it should "ignore deletion of permission X role relations that do not exists" in {
    val module = createEmptyModule()
    val initCommands = List(CreateRole("name"), CreatePermission("code", "description"))

    val warmUpResult = serial(initCommands)(asyncCommandHandler(module).execute)
    whenReady(warmUpResult) { initResults =>
      val firstFailure = initResults.find(_.isLeft)
      firstFailure shouldBe empty withClue ": Failure on warm up commands"
      val permissionId = stateProjection(module).lastSnapshot().futureValue.allPermissionID.head
      val roleId = stateProjection(module).lastSnapshot().futureValue.allRoleId.head
      whenReady(asyncCommandHandler(module).execute(DeletePermissionsFromRole(Set(permissionId), roleId))) { result =>
        result should be(\/-)
      }
    }

  }

  override def buildGenerator(state: AtomicEventStoreProjection[RoleBasedAuthorizationState])
  : CommandGenerator[RoleBasedAuthorizationModelCommand] = new RoleBasedAuthorizationGenerators(state)

  override def postCommandValidation(state: RoleBasedAuthorizationState, command: RoleBasedAuthorizationModelCommand): Unit = command match {

    case CreateRole(roleName) =>
      val roles = state.allRoleId.map(state.roleById).filter(_.isDefined).map(_.get)
      roles.find(_.roleName == roleName) should not be empty withClue ": Role by name not found"
    case UpdateRoleName(roleID, roleName) =>
      state.roleById(roleID).get.roleName should be(roleName)
    case DeleteRole(roleID) =>
      state.allRoleId should not contain roleID
    case CreatePermission(name, description) =>
      val permissions = state.allPermissionID.map(state.permissionById).filter(_.isDefined).map(_.get)
      permissions.find(_.code == name) should not be empty withClue ": Permission by name not found"
      permissions.find(_.description == description) should not be empty withClue ": Permission by description not found"
    case DeletePermission(permissionId) =>
      state.allPermissionID.find(_ == permissionId) shouldBe empty withClue ": Permission found after delete"
      state.allRoleId
        .map(state.getPermissionsForRole)
        .filter(_.contains(permissionId)) shouldBe empty withClue ": Permission found as part of a role"
    case AddPermissionsToRole(permissionSetId, roleID) =>
      withClue(": after add permission to role, the relation do not contains the role") {
        permissionSetId.foreach(permissionId =>
          state.getPermissionsForRole(roleID) should contain(permissionId)
        )
        permissionSetId.foreach(permissionId =>
          assert(state.isPermissionInRole(roleID, permissionId))
        )
      }
    case DeletePermissionsFromRole(permissionSetId, roleID) =>
      withClue(": after delete of permission to role, the relation is not updated") {
        permissionSetId.foreach(permissionId =>
          state.getPermissionsForRole(roleID) should not contain permissionId
        )
        permissionSetId.foreach(permissionId =>
          assert(!state.isPermissionInRole(roleID, permissionId))
        )

      }
    case UpdatePermissionDescription(permissionId, description) =>
      state.permissionById(permissionId).get.description should be(description)
  }

  override def validateState(state: RoleBasedAuthorizationState) {
    findInconsistentRoleID(state) shouldBe empty withClue ": A not existing role found by RoleID reference"
    findInconsistentPermissionID(state) shouldBe empty withClue ": A not existing permission found by PermissionID reference"
    findInconsistentPermissionIDInRoleRelation(state) shouldBe empty withClue ": A not existing permission found by role reference"
  }

  private def findInconsistentPermissionIDInRoleRelation(state: RoleBasedAuthorizationState) = {
    state
      .allRoleId
      .flatMap(roleId => state.getPermissionsForRole(roleId))
      .distinct
      .filter(permissionId => !state.permissionIdExists(permissionId))
      .toList
  }

  private def findInconsistentRoleID(state: RoleBasedAuthorizationState) = {
    state
      .allRoleId
      .filter(roleId => !state.roleIdExists(roleId))
      .toList
  }

  private def findInconsistentPermissionID(state: RoleBasedAuthorizationState) = {
    state
      .allPermissionID
      .filter(permissionId => !state.permissionIdExists(permissionId))
      .toList
  }


}
