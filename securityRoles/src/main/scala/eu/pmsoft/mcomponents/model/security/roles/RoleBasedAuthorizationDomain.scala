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

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._

import scalaz._

final class RoleBasedAuthorizationDomain extends DomainSpecification {
  type Command = RoleBasedAuthorizationModelCommand
  type Event = RoleBasedAuthorizationEvent
  type Aggregate = RoleBasedAuthorizationAggregate
  type ConstraintScope = RoleBasedAuthorizationConstraintScope
  type State = RoleBasedAuthorizationState
  type SideEffects = RoleBasedAuthorizationLocalSideEffects
}

final class RoleBasedAuthorizationEventSerializationSchema extends EventSerializationSchema[RoleBasedAuthorizationDomain] {
  override def mapToEvent(data: EventDataWithNr): RoleBasedAuthorizationEvent = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    data.eventBytes.unpickle[RoleBasedAuthorizationEvent]
  }

  override def buildConstraintReference(constraintScope: RoleBasedAuthorizationDomain#ConstraintScope): ConstraintReference = constraintScope match {
    case RoleNameAggregate(roleName)   => ConstraintReference(0, roleName)
    case PermissionCodeAggregate(code) => ConstraintReference(0, code)
  }

  override def buildAggregateReference(aggregate: RoleBasedAuthorizationAggregate): AggregateReference = aggregate match {
    case RoleIdAggregate(roleID)             => AggregateReference(0, roleID.id)
    case PermissionIdAggregate(permissionId) => AggregateReference(3, permissionId.id)
  }

  override def eventToData(event: RoleBasedAuthorizationEvent): EventData = {
    import scala.pickling.Defaults._
    import scala.pickling.binary._
    EventData(event.pickle.value)
  }

}

final class RoleBasedAuthorizationHandlerLogic extends DomainLogic[RoleBasedAuthorizationDomain]
    with RoleBasedAuthorizationValidations {

  override def calculateConstraints(command: RoleBasedAuthorizationModelCommand, state: RoleBasedAuthorizationState): CommandToConstraints[RoleBasedAuthorizationDomain] = command match {
    case CreateRole(roleName)                                   => \/-(Set(RoleNameAggregate(roleName)))
    case DeleteRole(roleID)                                     => \/-(Set())
    case UpdateRoleName(roleID, roleName)                       => \/-(Set(RoleNameAggregate(roleName)))
    case CreatePermission(code, description)                    => \/-(Set(PermissionCodeAggregate(code)))
    case UpdatePermissionDescription(permissionId, description) => \/-(Set())
    case DeletePermission(permissionId)                         => \/-(Set())
    case AddPermissionsToRole(permissionIdSet, roleID)          => \/-(Set())
    case DeletePermissionsFromRole(permissionIdSet, roleID)     => \/-(Set())
  }

  override def calculateRootAggregate(command: RoleBasedAuthorizationModelCommand, state: RoleBasedAuthorizationState): CommandToAggregateScope[RoleBasedAuthorizationDomain] = command match {
    case CreateRole(roleName)                                   => \/-(Set())
    case DeleteRole(roleID)                                     => \/-(Set(RoleIdAggregate(roleID)))
    case UpdateRoleName(roleID, roleName)                       => \/-(Set(RoleIdAggregate(roleID)))
    case CreatePermission(code, description)                    => \/-(Set())
    case UpdatePermissionDescription(permissionId, description) => \/-(Set(PermissionIdAggregate(permissionId)))
    case DeletePermission(permissionId)                         => \/-(Set(PermissionIdAggregate(permissionId)))
    case AddPermissionsToRole(permissionIdSet, roleID)          => \/-(permissionIdSet.map(PermissionIdAggregate) ++ Set(RoleIdAggregate(roleID)))
    case DeletePermissionsFromRole(permissionIdSet, roleID)     => \/-(permissionIdSet.map(PermissionIdAggregate) ++ Set(RoleIdAggregate(roleID)))
  }

  //  override def calculateTransactionScope(command: RoleBasedAuthorizationModelCommand, state: RoleBasedAuthorizationState): CommandToAggregates[RoleBasedAuthorizationDomain] = command match {
  //    case CreateRole(roleName)                                   => \/-(Set(RoleNameAggregate(roleName)))
  //    case DeleteRole(roleID)                                     => \/-(Set(RoleIdAggregate(roleID)))
  //    case UpdateRoleName(roleID, roleName)                       => \/-(Set(RoleIdAggregate(roleID), RoleNameAggregate(roleName)))
  //    case CreatePermission(code, description)                    => \/-(Set(PermissionCodeAggregate(code)))
  //    case UpdatePermissionDescription(permissionId, description) => \/-(Set(PermissionIdAggregate(permissionId)))
  //    case DeletePermission(permissionId)                         => \/-(Set(PermissionIdAggregate(permissionId)))
  //    case AddPermissionsToRole(permissionIdSet, roleID)          => \/-(permissionIdSet.map(PermissionIdAggregate) ++ Set(RoleIdAggregate(roleID)))
  //    case DeletePermissionsFromRole(permissionIdSet, roleID)     => \/-(permissionIdSet.map(PermissionIdAggregate) ++ Set(RoleIdAggregate(roleID)))
  //  }

  override def executeCommand(
    command:                RoleBasedAuthorizationModelCommand,
    atomicTransactionScope: AtomicTransactionScope[RoleBasedAuthorizationDomain]
  )(implicit state: RoleBasedAuthorizationState, sideEffects: RoleBasedAuthorizationLocalSideEffects): CommandToEventsResult[RoleBasedAuthorizationDomain] =
    command match {

      case CreateRole(roleName) => for {
        roleNameValid <- validName(roleName)
        roleName <- ensureRoleNamesAreUnique(roleName)
      } yield {
        val roleID = sideEffects.generateUniqueRoleId()
        CommandModelResult(List(AccessRoleCreated(roleID, roleNameValid)), RoleIdAggregate(roleID))
      }
      case UpdateRoleName(roleID, roleName) => for {
        roleNameValid <- validName(roleName)
        currRole <- existingRoleById(roleID)
        roleName <- ensureRoleNamesAreUnique(roleName, currRole)
      } yield if (currRole.roleName == roleNameValid) {
        CommandModelResult(List(), RoleIdAggregate(roleID))
      }
      else {
        CommandModelResult(List(AccessRoleNameUpdated(currRole.roleId, roleNameValid)), RoleIdAggregate(roleID))
      }

      case DeleteRole(roleID) => for {
        roleID <- existingRoleId(roleID)
      } yield {
        CommandModelResult(List(AccessRoleDeleted(roleID)), RoleIdAggregate(roleID))
      }

      case CreatePermission(code, description) => for {
        codeValid <- validCode(code)
        descriptionValid <- validDescription(description)
      } yield {
        val permissionId = sideEffects.generateUniquePermissionId()
        CommandModelResult(
          List(AccessPermissionCreated(permissionId, codeValid, descriptionValid)),
          PermissionIdAggregate(permissionId)
        )
      }

      case UpdatePermissionDescription(permissionId, description) => for {
        descriptionValid <- validDescription(description)
        permission <- existingPermissionByID(permissionId)
      } yield if (permission.description == descriptionValid) {
        CommandModelResult(List(), PermissionIdAggregate(permissionId))
      }
      else {
        CommandModelResult(
          List(AccessPermissionDescriptionUpdated(permission.permissionId, descriptionValid)),
          PermissionIdAggregate(permissionId)
        )
      }

      case DeletePermission(permissionId) => for {
        permission <- existingPermissionByID(permissionId)
      } yield CommandModelResult(
        List(AccessPermissionDeleted(permission.permissionId)),
        PermissionIdAggregate(permissionId)
      )

      case AddPermissionsToRole(permissionId, roleID) => for {
        permissionSetId <- existingPermissionSetID(permissionId)
        roleID <- existingRoleId(roleID)
      } yield CommandModelResult(
        permissionSetId.map(PermissionInRoleAdded(_, roleID)).toList,
        RoleIdAggregate(roleID)
      )

      case DeletePermissionsFromRole(permissionId, roleID) => for {
        permissionSetId <- existingPermissionSetID(permissionId)
        roleID <- existingRoleId(roleID)
      } yield CommandModelResult(
        permissionSetId.filter(state.isPermissionInRole(roleID, _))
        .map(PermissionInRoleDeleted(_, roleID))
        .toList,
        RoleIdAggregate(roleID)
      )
    }
}

import eu.pmsoft.mcomponents.model.security.roles.RoleBasedAuthorizationModel._

trait RoleBasedAuthorizationValidations {

  def validCode(roleCode: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!roleCode.isEmpty) {
      \/-(roleCode)
    }
    else {
      -\/(EventSourceCommandFailed(invalidRoleName.code))
    }

  def validName(name: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!name.isEmpty) {
      \/-(name)
    }
    else {
      -\/(EventSourceCommandFailed(invalidName.code))
    }

  def ensureRoleNamesAreUnique(name: String, roleInContext: AccessRole)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (roleInContext.roleName == name) {
      \/-(name)
    }
    else {
      ensureRoleNamesAreUnique(name)
    }

  def ensureRoleNamesAreUnique(name: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (state.roleByName(name).isEmpty) {
      \/-(name)
    }
    else {
      -\/(EventSourceCommandFailed(nameForRoleUsed.code))
    }

  def validDescription(description: String)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[String] =
    if (!description.isEmpty) {
      \/-(description)
    }
    else {
      -\/(EventSourceCommandFailed(invalidRoleDescription.code))
    }

  def existingRoleById(roleId: RoleID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[AccessRole] =
    existingRoleId(roleId).map(state.roleById(_).get)

  def existingRoleId(roleId: RoleID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[RoleID] =
    if (state.roleIdExists(roleId)) {
      \/-(roleId)
    }
    else {
      -\/(EventSourceCommandFailed(notExistingRoleID.code))
    }

  def existingPermissionByID(permissionID: PermissionID)(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[Permission] =
    state.permissionById(permissionID) match {
      case Some(permission) => \/-(permission)
      case None             => -\/(EventSourceCommandFailed(notExistingPermissionID.code))
    }

  def existingPermissionSetID(permissionSetID: Set[PermissionID])(implicit state: RoleBasedAuthorizationState): CommandPartialValidation[Set[PermissionID]] =
    if (permissionSetID.forall(state.permissionIdExists)) {
      \/-(permissionSetID)
    }
    else {
      -\/(EventSourceCommandFailed(notExistingPermissionID.code))
    }

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
