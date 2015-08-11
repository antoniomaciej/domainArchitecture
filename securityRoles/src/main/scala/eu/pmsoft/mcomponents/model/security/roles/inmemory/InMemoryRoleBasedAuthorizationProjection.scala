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
 */

package eu.pmsoft.mcomponents.model.security.roles.inmemory

import eu.pmsoft.mcomponents.eventsourcing.inmemory.EventStoreWithProjectionInMemoryLogic
import eu.pmsoft.mcomponents.eventsourcing.{EventStoreID, EventStoreIdentification}
import eu.pmsoft.mcomponents.model.security.roles._

import scala.reflect.{ClassTag, classTag}

class RoleBasedEventStoreWithProjectionInMemoryLogic extends EventStoreWithProjectionInMemoryLogic[RoleBasedAuthorizationEvent,
  RoleBasedAuthorizationAggregate,
  RoleBasedAuthorizationStateInMemory] {
  override def buildInitialState(): RoleBasedAuthorizationStateInMemory = RoleBasedAuthorizationStateInMemory()

  override def projectSingleEvent(state: RoleBasedAuthorizationStateInMemory,
                                  event: RoleBasedAuthorizationEvent): RoleBasedAuthorizationStateInMemory =
    event match {
      case AccessRoleCreated(roleId, roleName) => state.addRole(AccessRole(roleId, roleName))
      case AccessRoleNameUpdated(roleId, roleName) => state.updateRoleName(roleId, roleName)
      case AccessRoleDeleted(roleID) => state.deleteRole(roleID)
      case AccessPermissionCreated(permissionId, name, description) => state.addPermission(Permission(permissionId, name, description))
      case AccessPermissionDescriptionUpdated(permissionId, description) => state.updatePermissionDescription(permissionId, description)
      case AccessPermissionDeleted(permissionId) => state.deletePermission(permissionId)
      case PermissionInRoleAdded(permissionId, roleID) => state.addPermissionToRole(roleID, permissionId)
      case PermissionInRoleDeleted(permissionId, roleID) => state.deletePermissionToRole(roleID, permissionId)
    }
}

class RoleBasedEventStoreIdentification extends EventStoreIdentification[RoleBasedAuthorizationEvent,
  RoleBasedAuthorizationAggregate] {
  override def id: EventStoreID = EventStoreID("InMemoryRoleBasedAuthorizationProjection")

  override def aggregateRootType: ClassTag[RoleBasedAuthorizationAggregate] = classTag[RoleBasedAuthorizationAggregate]

  override def rootEventType: ClassTag[RoleBasedAuthorizationEvent] = classTag[RoleBasedAuthorizationEvent]
}
