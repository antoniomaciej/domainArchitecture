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

import eu.pmsoft.mcomponents.eventsourcing.EventSourceDataModel._
import eu.pmsoft.mcomponents.eventsourcing.{CommandToTransactionScope, DomainLogic, EventSourceCommandFailed}

import scalaz._

trait RoleBasedAuthorizationModule {

}


trait RoleBasedAuthorizationState {
  //Roles
  def allRoleId: Stream[RoleID]

  def roleById(roleId: RoleID): Option[AccessRole]

  def roleByName(roleName: String): Option[AccessRole]

  def roleIdExists(roleId: RoleID): Boolean

  //Permissions
  def allPermissionID: Stream[PermissionID]

  def permissionIdExists(permissionID: PermissionID): Boolean

  def permissionById(permissionID: PermissionID): Option[Permission]

  def permissionByCode(code: String): Option[Permission]

  // Permission X Role relation
  def isPermissionInRole(roleId: RoleID, permissionID: PermissionID): Boolean

  def getPermissionsForRole(roleId: RoleID): Set[PermissionID]
}

trait RoleBasedAuthorizationLocalSideEffects {
  def generateUniquePermissionId(): PermissionID

  def generateUniqueRoleId(): RoleID
}

final class RoleBaseAuthorizationCommandToTransactionScope
  extends CommandToTransactionScope[RoleBasedAuthorizationModelCommand, RoleBasedAuthorizationAggregate, RoleBasedAuthorizationState] {

  override def calculateTransactionScope(command: RoleBasedAuthorizationModelCommand, state: RoleBasedAuthorizationState):
  CommandToAggregateResult[RoleBasedAuthorizationAggregate] = command match {
    case CreateRole(roleName) => \/-(Set(RoleNameAggregate(roleName)))
    case DeleteRole(roleID) => \/-(Set(RoleIdAggregate(roleID)))
    case UpdateRoleName(roleID, roleName) => \/-(Set(RoleIdAggregate(roleID), RoleNameAggregate(roleName)))
    case CreatePermission(code, description) => \/-(Set(PermissionCodeAggregate(code)))
    case UpdatePermissionDescription(permissionId, description) => \/-(Set(PermissionIdAggregate(permissionId)))
    case DeletePermission(permissionId) => \/-(Set(PermissionIdAggregate(permissionId)))
    case AddPermissionsToRole(permissionIdSet, roleID) => \/-(permissionIdSet.map(PermissionIdAggregate) ++ Set(RoleIdAggregate(roleID)))
    case DeletePermissionsFromRole(permissionIdSet, roleID) => \/-(permissionIdSet.map(PermissionIdAggregate) ++ Set(RoleIdAggregate(roleID)))
  }
}

final class RoleBasedAuthorizationHandlerLogic(val sideEffects: RoleBasedAuthorizationLocalSideEffects)
  extends DomainLogic[RoleBasedAuthorizationModelCommand, RoleBasedAuthorizationEvent, RoleBasedAuthorizationAggregate, RoleBasedAuthorizationState]
  with RoleBasedAuthorizationValidations {

  override def executeCommand(command: RoleBasedAuthorizationModelCommand,
                              transactionScope: Map[RoleBasedAuthorizationAggregate, Long])
                             (implicit state: RoleBasedAuthorizationState):
  CommandToEventsResult[RoleBasedAuthorizationEvent] = command match {

    case CreateRole(roleName) => for {
      roleNameValid <- validName(roleName)
    } yield List(AccessRoleCreated(sideEffects.generateUniqueRoleId(), roleNameValid))

    case UpdateRoleName(roleID, roleName) => for {
      roleNameValid <- validName(roleName)
      currRole <- existingRoleById(roleID)
    } yield if (currRole.roleName == roleNameValid) {
        List()
      } else {
        List(AccessRoleNameUpdated(currRole.roleId, roleNameValid))
      }

    case DeleteRole(roleID) => for {
      roleID <- existingRoleId(roleID)
    } yield List(AccessRoleDeleted(roleID))

    case CreatePermission(code, description) => for {
      codeValid <- validCode(code)
      descriptionValid <- validDescription(description)
    } yield List(AccessPermissionCreated(sideEffects.generateUniquePermissionId(), codeValid, descriptionValid))

    case UpdatePermissionDescription(permissionId, description) => for {
      descriptionValid <- validDescription(description)
      permission <- existingPermissionByID(permissionId)
    } yield if (permission.description == descriptionValid) {
        List()
      } else {
        List(AccessPermissionDescriptionUpdated(permission.permissionId, descriptionValid))
      }

    case DeletePermission(permissionId) => for {
      permission <- existingPermissionByID(permissionId)
    } yield List(AccessPermissionDeleted(permission.permissionId))

    case AddPermissionsToRole(permissionId, roleID) => for {
      permissionSetId <- existingPermissionSetID(permissionId)
      roleID <- existingRoleId(roleID)
    } yield permissionSetId.map(PermissionInRoleAdded(_, roleID)).toList

    case DeletePermissionsFromRole(permissionId, roleID) => for {
      permissionSetId <- existingPermissionSetID(permissionId)
      roleID <- existingRoleId(roleID)
    } yield permissionSetId
        .filter(state.isPermissionInRole(roleID, _))
        .map(PermissionInRoleDeleted(_, roleID))
        .toList
  }
}

import eu.pmsoft.mcomponents.model.security.roles.RoleBasedAuthorizationModel._

trait RoleBasedAuthorizationValidations {

  def validCode(roleCode: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!roleCode.isEmpty) {
      \/-(roleCode)
    } else {
      -\/(EventSourceCommandFailed(invalidRoleName.code))
    }

  def validName(name: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!name.isEmpty) {
      \/-(name)
    } else {
      -\/(EventSourceCommandFailed(invalidName.code))
    }

  def validDescription(description: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!description.isEmpty) {
      \/-(description)
    } else {
      -\/(EventSourceCommandFailed(invalidRoleDescription.code))
    }

  def existingRoleById(roleId: RoleID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[AccessRole] =
    existingRoleId(roleId).map(state.roleById(_).get)

  def existingRoleId(roleId: RoleID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[RoleID] =
    if (state.roleIdExists(roleId)) {
      \/-(roleId)
    } else {
      -\/(EventSourceCommandFailed(notExistingRoleID.code))
    }

  def existingPermissionByID(permissionID: PermissionID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[Permission] =
    state.permissionById(permissionID) match {
      case Some(permission) => \/-(permission)
      case None => -\/(EventSourceCommandFailed(notExistingPermissionID.code))
    }

  def existingPermissionSetID(permissionSetID: Set[PermissionID])(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[Set[PermissionID]] =
    if (permissionSetID.forall(state.permissionIdExists)) {
      \/-(permissionSetID)
    } else {
      -\/(EventSourceCommandFailed(notExistingPermissionID.code))
    }
}

