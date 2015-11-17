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

import eu.pmsoft.mcomponents.eventsourcing._

object RoleBasedAuthorizationModel {

  val invalidRoleNameErrorCode = 2001L
  val invalidRoleName = EventSourceModelError(
    "invalid role name",
    EventSourceCommandError(invalidRoleNameErrorCode)
  )

  val invalidRoleDescriptionErrorCode = 2002L
  val invalidRoleDescription = EventSourceModelError(
    "invalid role description",
    EventSourceCommandError(invalidRoleDescriptionErrorCode)
  )

  val notExistingRoleIDErrorCode = 2003L
  val notExistingRoleID = EventSourceModelError(
    "roleId to not exist",
    EventSourceCommandError(notExistingRoleIDErrorCode)
  )

  val notExistingPermissionIDErrorCode = 2004L
  val notExistingPermissionID = EventSourceModelError(
    "permissionID do not exist",
    EventSourceCommandError(notExistingPermissionIDErrorCode)
  )

  val invalidNameErrorCode = 2005L
  val invalidName = EventSourceModelError(
    "invalid name",
    EventSourceCommandError(invalidNameErrorCode)
  )

  val nameForRoleUsedErrorCode = 2006L
  val nameForRoleUsed = EventSourceModelError(
    "a role with this name already exist",
    EventSourceCommandError(nameForRoleUsedErrorCode)
  )

}

case class RoleID(val id: Long) extends AnyVal

case class PermissionID(val id: Long) extends AnyVal

//Aggregated
sealed trait RoleBasedAuthorizationAggregate

case class RoleIdAggregate(roleID: RoleID) extends RoleBasedAuthorizationAggregate

case class RoleNameAggregate(roleName: String) extends RoleBasedAuthorizationAggregate

case class PermissionCodeAggregate(code: String) extends RoleBasedAuthorizationAggregate

case class PermissionIdAggregate(permissionId: PermissionID) extends RoleBasedAuthorizationAggregate

//Model entities

case class AccessRole(roleId: RoleID, roleName: String)

case class Permission(permissionId: PermissionID, code: String, description: String)

// RoleBasedAuthorizationModel commands
//Role
sealed trait RoleBasedAuthorizationModelCommand

case class CreateRole(roleName: String) extends RoleBasedAuthorizationModelCommand

case class DeleteRole(roleID: RoleID) extends RoleBasedAuthorizationModelCommand

case class UpdateRoleName(roleID: RoleID, roleName: String) extends RoleBasedAuthorizationModelCommand

//Permission
case class CreatePermission(code: String, description: String) extends RoleBasedAuthorizationModelCommand

case class UpdatePermissionDescription(permissionId: PermissionID, description: String) extends RoleBasedAuthorizationModelCommand

case class DeletePermission(permissionId: PermissionID) extends RoleBasedAuthorizationModelCommand

//Permission x Roles
case class AddPermissionsToRole(permissionIdSet: Set[PermissionID], roleID: RoleID) extends RoleBasedAuthorizationModelCommand

case class DeletePermissionsFromRole(permissionIdSet: Set[PermissionID], roleID: RoleID) extends RoleBasedAuthorizationModelCommand

// RoleBasedAuthorizationModel events

sealed trait RoleBasedAuthorizationEvent

//Roles
case class AccessRoleCreated(roleId: RoleID, roleName: String) extends RoleBasedAuthorizationEvent

case class AccessRoleNameUpdated(roleId: RoleID, roleName: String) extends RoleBasedAuthorizationEvent

case class AccessRoleDeleted(roleID: RoleID) extends RoleBasedAuthorizationEvent

//Permission
case class AccessPermissionCreated(permissionId: PermissionID, name: String, description: String) extends RoleBasedAuthorizationEvent

case class AccessPermissionDescriptionUpdated(permissionId: PermissionID, description: String) extends RoleBasedAuthorizationEvent

case class AccessPermissionDeleted(permissionId: PermissionID) extends RoleBasedAuthorizationEvent

//Permission x Roles
case class PermissionInRoleAdded(permissionId: PermissionID, roleID: RoleID) extends RoleBasedAuthorizationEvent

case class PermissionInRoleDeleted(permissionId: PermissionID, roleID: RoleID) extends RoleBasedAuthorizationEvent

