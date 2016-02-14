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

package eu.pmsoft.mcomponents.model.security.roles

import java.util.concurrent.atomic.AtomicLong

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore._
import monocle.function._
import monocle.macros.GenLens
import monocle.std._

import scala.language.{ higherKinds, postfixOps }
import scala.reflect._

object RoleBasedAuthorizationDomainModule {
  val eventStoreReference: EventStoreReference[RoleBasedAuthorizationDomain] =
    EventStoreReference[RoleBasedAuthorizationDomain](EventStoreID("RoleBasedAuthorizationDomain"), classTag[RoleBasedAuthorizationEvent], classTag[RoleBasedAuthorizationAggregate])
}

class RoleBasedAuthorizationDomainModule(implicit val eventSourcingConfiguration: EventSourcingConfiguration) extends DomainModule[RoleBasedAuthorizationDomain] {
  override lazy val logic: DomainLogic[RoleBasedAuthorizationDomain] = new RoleBasedAuthorizationHandlerLogic()

  override lazy val schema: EventSerializationSchema[RoleBasedAuthorizationDomain] = new RoleBasedAuthorizationEventSerializationSchema

  override lazy val eventStore: EventStore[RoleBasedAuthorizationDomain] with VersionedEventStoreView[RoleBasedAuthorizationAggregate, RoleBasedAuthorizationState] =
    EventStoreProvider.createEventStore[RoleBasedAuthorizationDomain, RoleBasedAuthorizationStateInMemory](new RoleBasedEventStoreAtomicProjection(), schema, RoleBasedAuthorizationDomainModule.eventStoreReference)

  override lazy val sideEffects: RoleBasedAuthorizationLocalSideEffects = new LocalThreadRoleBasedAuthorizationLocalSideEffects()
}

class LocalThreadRoleBasedAuthorizationLocalSideEffects extends RoleBasedAuthorizationLocalSideEffects {

  val roleIdCounter = new AtomicLong(0)
  val permissionIdCounter = new AtomicLong(0)

  override def generateUniqueRoleId(): RoleID = new RoleID(roleIdCounter.getAndAdd(1))

  override def generateUniquePermissionId(): PermissionID = new PermissionID(permissionIdCounter.getAndAdd(1))
}

class RoleBasedEventStoreAtomicProjection extends EventStoreAtomicProjectionCreationLogic[RoleBasedAuthorizationDomain, RoleBasedAuthorizationStateInMemory] {
  override def buildInitialState(): RoleBasedAuthorizationStateInMemory = RoleBasedAuthorizationStateInMemory()

  override def projectSingleEvent(
    state: RoleBasedAuthorizationStateInMemory,
    event: RoleBasedAuthorizationEvent
  ): RoleBasedAuthorizationStateInMemory =
    event match {
      case AccessRoleCreated(roleId, roleName)                           => state.addRole(AccessRole(roleId, roleName))
      case AccessRoleNameUpdated(roleId, roleName)                       => state.updateRoleName(roleId, roleName)
      case AccessRoleDeleted(roleID)                                     => state.deleteRole(roleID)
      case AccessPermissionCreated(permissionId, name, description)      => state.addPermission(Permission(permissionId, name, description))
      case AccessPermissionDescriptionUpdated(permissionId, description) => state.updatePermissionDescription(permissionId, description)
      case AccessPermissionDeleted(permissionId)                         => state.deletePermission(permissionId)
      case PermissionInRoleAdded(permissionId, roleID)                   => state.addPermissionToRole(roleID, permissionId)
      case PermissionInRoleDeleted(permissionId, roleID)                 => state.deletePermissionToRole(roleID, permissionId)
    }
}

object RoleBasedAuthorizationStateLenses {

  val stateLens = GenLens[RoleBasedAuthorizationStateInMemory]
  val _roleByID = stateLens(_.roleByID)
  val _permissionByID = stateLens(_.permissionByID)
  val _roleToPermission = stateLens(_.roleToPermission)

  val accessRoleLens = GenLens[AccessRole]
  val _roleId = accessRoleLens(_.roleId)
  val _roleName = accessRoleLens(_.roleName)

  val permissionLens = GenLens[Permission]
  val _permissionId = permissionLens(_.permissionId)
  val _code = permissionLens(_.code)
  val _description = permissionLens(_.description)
}

case class RoleBasedAuthorizationStateInMemory(
    roleByID:         Map[RoleID, AccessRole]        = Map(),
    permissionByID:   Map[PermissionID, Permission]  = Map(),
    roleToPermission: Map[RoleID, Set[PermissionID]] = Map(),
    version:          EventStoreVersion              = EventStoreVersion.zero
) extends RoleBasedAuthorizationState {

  import RoleBasedAuthorizationStateLenses._

  //Roles
  override def allRoleId: Stream[RoleID] = roleByID.keysIterator.toStream

  override def roleIdExists(roleId: RoleID): Boolean = roleByID.keySet.contains(roleId)

  override def roleById(roleId: RoleID): Option[AccessRole] =
    (_roleByID ^|-? index(roleId))
      .getOption(this)

  override def roleByName(roleName: String): Option[AccessRole] = roleByID.values.find(_.roleName == roleName)

  def addRole(role: AccessRole): RoleBasedAuthorizationStateInMemory =
    (_roleByID ^|-> at(role.roleId))
      .set(Some(role))(this)

  def updateRoleName(roleId: RoleID, roleName: String): RoleBasedAuthorizationStateInMemory =
    (_roleByID ^|-? index(roleId) ^|-> _roleName)
      .set(roleName)(this)

  def deleteRole(roleID: RoleID): RoleBasedAuthorizationStateInMemory =
    (_roleByID ^|-> at(roleID))
      .set(None)(this)

  //Permissions
  override def permissionById(permissionID: PermissionID): Option[Permission] =
    (_permissionByID ^|-> at(permissionID))
      .get(this)

  override def permissionByCode(code: String): Option[Permission] = permissionByID.values.find(_.code == code)

  def deletePermission(permissionId: PermissionID): RoleBasedAuthorizationStateInMemory = {
    val deletePermissionF = (_permissionByID ^|-> at(permissionId)).set(None)
    val deleteFromRolesF = (_roleToPermission ^|->> each).modify(_ - permissionId)
    (deleteFromRolesF compose deletePermissionF)(this)
  }

  def addPermission(permission: Permission): RoleBasedAuthorizationStateInMemory =
    (_permissionByID ^|-> at(permission.permissionId))
      .set(Some(permission))(this)

  override def allPermissionID: Stream[PermissionID] = permissionByID.keys.toStream

  override def permissionIdExists(permissionID: PermissionID): Boolean = permissionByID.keySet.contains(permissionID)

  def updatePermissionDescription(permissionId: PermissionID, description: String): RoleBasedAuthorizationStateInMemory =
    (_permissionByID ^|-? index(permissionId) ^|-> _description)
      .set(description)(this)

  // Permission X Role relation

  def deletePermissionToRole(
    roleId:       RoleID,
    permissionId: PermissionID
  ): RoleBasedAuthorizationStateInMemory =
    (_roleToPermission ^|-> at(roleId)).modify(
      _.map((current) => current - permissionId)
    )(this)

  def addPermissionToRole(
    roleId:       RoleID,
    permissionId: PermissionID
  ): RoleBasedAuthorizationStateInMemory =
    (_roleToPermission ^|-> at(roleId))
      .modify {
        case None              => Some(Set(permissionId))
        case Some(permissions) => Some(permissions + permissionId)
      }(this)

  override def isPermissionInRole(
    roleId:       RoleID,
    permissionId: PermissionID
  ): Boolean =
    (_roleToPermission ^|-> at(roleId))
      .get(this) match {
        case None              => false
        case Some(permissions) => permissions.contains(permissionId)
      }

  override def getPermissionsForRole(roleId: RoleID): Set[PermissionID] =
    (_roleToPermission ^|-> at(roleId))
      .get(this)
      .getOrElse(Set())

}
