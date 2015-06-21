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
 */

package eu.pmsoft.domain.model.security

import eu.pmsoft.domain.model.EventSourceEngine.{CommandPartialValidation, CommandToEventsResult}
import eu.pmsoft.domain.model._

import scalaz._

trait RoleBasedAuthorizationModule {

}


trait RoleBasedAuthorizationState {
  //Roles
  def allRoleId: Stream[RoleID]

  def roleById(roleId: RoleID): Option[AccessRole]

  def roleIdExists(roleId: RoleID): Boolean

  //Permissions
  def allPermissionID: Stream[PermissionID]

  def permissionIdExists(permissionID: PermissionID): Boolean

  def permissionById(permissionID: PermissionID): Option[Permission]

  // Permission X Role relation
  def isPermissionInRole(roleId: RoleID, permissionID: PermissionID): Boolean

  def getPermissionsForRole(roleId: RoleID): Set[PermissionID]
}

trait RoleBasedAuthorizationLocalSideEffects {
  def generateUniquePermissionId(): PermissionID

  def generateUniqueRoleId(): RoleID
}

final class RoleBasedAuthorizationHandlerLogic(val sideEffects: RoleBasedAuthorizationLocalSideEffects) extends
DomainLogic[RoleBasedAuthorizationModelCommand, RoleBasedAuthorizationEvent, RoleBasedAuthorizationState]
with RoleBasedAuthorizationValidations {

  override def executeCommand(command: RoleBasedAuthorizationModelCommand)
                             (implicit state: RoleBasedAuthorizationState):
  CommandToEventsResult[RoleBasedAuthorizationEvent] = command match {
    case CreateRole(roleName) => for {
      name <- validName(roleName)
    } yield List(AccessRoleCreated(sideEffects.generateUniqueRoleId(), name))
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
    case CreatePermission(name, description) => for {
      nameValid <- validName(name)
      descriptionValid <- validDescription(description)
    } yield List(AccessPermissionCreated(sideEffects.generateUniquePermissionId(), nameValid, descriptionValid))
    case UpdatePermissionName(permissionId, name) => for {
      nameValid <- validName(name)
      permission <- existingPermissionByID(permissionId)
    } yield List(AccessPermissionNameUpdated(permission.permissionId, nameValid))
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
    case AddPermissionToRole(permissionId, roleID) => for {
      permissionId <- existingPermissionID(permissionId)
      roleID <- existingRoleId(roleID)
    } yield List(PermissionInRoleAdded(permissionId, roleID))
    case DeletePermissionFromRole(permissionId, roleID) => for {
      permissionId <- existingPermissionID(permissionId)
      roleID <- existingRoleId(roleID)
    } yield {
        if (state.isPermissionInRole(roleID, permissionId)) {
          List(PermissionInRoleDeleted(permissionId, roleID))
        } else {
          List()
        }
      }
  }
}

import RoleBasedAuthorizationModel._
trait RoleBasedAuthorizationValidations {

  def validName(roleName: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!roleName.isEmpty) {
      \/-(roleName)
    } else {
      -\/(invalidRoleName.code)
    }

  def validDescription(description: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!description.isEmpty) {
      \/-(description)
    } else {
      -\/(invalidRoleDescription.code)
    }

  def existingRoleById(roleId: RoleID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[AccessRole] =
    existingRoleId(roleId).map(state.roleById(_).get)

  def existingRoleId(roleId: RoleID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[RoleID] =
    if (state.roleIdExists(roleId)) {
      \/-(roleId)
    } else {
      -\/(notExistingRoleID.code)
    }

  def existingPermissionByID(permissionID: PermissionID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[Permission] =
    existingPermissionID(permissionID).map(state.permissionById(_).get)

  def existingPermissionID(permissionID: PermissionID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[PermissionID] =
    if (state.permissionIdExists(permissionID)) {
      \/-(permissionID)
    } else {
      -\/(notExistingPermissionID.code)
    }
}
