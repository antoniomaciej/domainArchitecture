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

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.minstance.ReqResDataModel._
import eu.pmsoft.mcomponents.minstance._
import eu.pmsoft.mcomponents.model.security.roles._

import scala.concurrent.{ ExecutionContext, Future }
import scalaz.Scalaz._
import scalaz._

trait RoleBasedAuthorizationModule extends ApiModuleProvided[RoleBasedAuthorizationDomain] {

  lazy val roleBasedAuthorizationApi: RoleBasedAuthorizationApi = new RoleBasedAuthorizationRequestDispatcher(cmdApi)

}

object RoleBasedAuthorizationApi {

  implicit val requestErrorDomain = RequestErrorDomain("RoleBasedAuthorization")
}

trait RoleBasedAuthorizationApi {

  def createRole(req: CreateRoleRequest): Future[RequestResult[CreateRoleResponse]]

  def deleteRole(req: DeleteRoleRequest): Future[RequestResult[DeleteRoleResponse]]

  def updateRole(req: UpdateRoleNameRequest): Future[RequestResult[UpdateRoleNameResponse]]

  def getRoles(req: GetRolesRequest): Future[RequestResult[GetRolesResponse]]

  def createPermission(req: CreatePermissionRequest): Future[RequestResult[CreatePermissionResponse]]

  def getPermissions(req: GetPermissionsRequest): Future[RequestResult[GetPermissionsResponse]]

  def deletePermission(req: DeletePermissionRequest): Future[RequestResult[DeletePermissionResponse]]

  def updatePermission(req: UpdatePermissionDescriptionRequest): Future[RequestResult[UpdatePermissionDescriptionResponse]]

  def addPermissionToRole(req: AddPermissionsToRoleRequest): Future[RequestResult[AddPermissionsToRoleResponse]]

  def deletePermissionFromRole(req: DeletePermissionsFromRoleRequest): Future[RequestResult[DeletePermissionsFromRoleResponse]]

  def getRolesPermissions(req: GetRolesPermissionsRequest): Future[RequestResult[GetRolesPermissionsResponse]]
}

class RoleBasedAuthorizationRequestDispatcher(val commandApi: DomainCommandApi[RoleBasedAuthorizationDomain])(implicit val executionContext: ExecutionContext)
    extends RoleBasedAuthorizationApi with RoleBasedAuthorizationExtractorFromProjection {

  import ReqResDataModel._
  import RoleBasedAuthorizationApi._

  override def createRole(req: CreateRoleRequest): Future[RequestResult[CreateRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(CreateRole(req.roleName)).map(_.asResponse))
    response <- EitherT(findRoleByName(cmdConfirmation, req.roleName))
  } yield response).run

  override def getRoles(req: GetRolesRequest): Future[RequestResult[GetRolesResponse]] = {
    val state = commandApi.atomicProjection.lastSnapshot()
    Future.successful(\/-(
      GetRolesResponse(state.allRoleId.map(state.roleById(_).get).toSet)
    ))
  }

  override def getPermissions(req: GetPermissionsRequest): Future[RequestResult[GetPermissionsResponse]] = {
    val state = commandApi.atomicProjection.lastSnapshot()
    Future.successful(\/-(
      GetPermissionsResponse(state.allPermissionID.map(state.permissionById(_).get).toSet)
    ))
  }

  override def deleteRole(req: DeleteRoleRequest): Future[RequestResult[DeleteRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(DeleteRole(req.roleID)).map(_.asResponse))
  } yield DeleteRoleResponse()).run

  override def updateRole(req: UpdateRoleNameRequest): Future[RequestResult[UpdateRoleNameResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(UpdateRoleName(req.roleID, req.newRoleName)).map(_.asResponse))
  } yield UpdateRoleNameResponse()).run

  override def updatePermission(req: UpdatePermissionDescriptionRequest): Future[RequestResult[UpdatePermissionDescriptionResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(UpdatePermissionDescription(req.permissionId, req.description)).map(_.asResponse))
  } yield UpdatePermissionDescriptionResponse()).run

  override def getRolesPermissions(req: GetRolesPermissionsRequest): Future[RequestResult[GetRolesPermissionsResponse]] = {
    val state = commandApi.atomicProjection.lastSnapshot()
    Future.successful(\/-(
      GetRolesPermissionsResponse(req.roleID.map { roleId => roleId -> state.getPermissionsForRole(roleId) }.toMap)
    ))
  }

  override def createPermission(req: CreatePermissionRequest): Future[RequestResult[CreatePermissionResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(CreatePermission(req.code, req.description)).map(_.asResponse))
    response <- EitherT(findPermissionByName(cmdConfirmation, req.code))
  } yield response).run

  override def deletePermission(req: DeletePermissionRequest): Future[RequestResult[DeletePermissionResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(DeletePermission(req.permissionId)).map(_.asResponse))
  } yield DeletePermissionResponse()).run

  override def addPermissionToRole(req: AddPermissionsToRoleRequest): Future[RequestResult[AddPermissionsToRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(AddPermissionsToRole(req.permissionsToAdd, req.roleID)).map(_.asResponse))
  } yield AddPermissionsToRoleResponse()).run

  override def deletePermissionFromRole(req: DeletePermissionsFromRoleRequest): Future[RequestResult[DeletePermissionsFromRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandApi.commandHandler.execute(DeletePermissionsFromRole(req.permissionsToDelete, req.roleID)).map(_.asResponse))
  } yield DeletePermissionsFromRoleResponse()).run
}

trait RoleBasedAuthorizationExtractorFromProjection {

  import ReqResDataModel._
  import RoleBasedAuthorizationApi._

  implicit def executionContext: ExecutionContext

  def commandApi: DomainCommandApi[RoleBasedAuthorizationDomain]

  def findPermissionByName(cmdResult: EventSourceCommandConfirmation[RoleBasedAuthorizationDomain#Aggregate], code: String): Future[RequestResult[CreatePermissionResponse]] =
    commandApi.atomicProjection.atLeastOn(cmdResult.storeVersion).map { state =>
      state.projection.permissionByCode(code) match {
        case Some(permission) => \/-(CreatePermissionResponse(permission.permissionId))
        case None             => -\/(RoleBasedAuthorizationRequestModel.permissionNotFoundAfterInsert.toResponseError)
      }
    }

  def findRoleByName(cmdResult: EventSourceCommandConfirmation[RoleBasedAuthorizationDomain#Aggregate], roleName: String): Future[RequestResult[CreateRoleResponse]] =
    commandApi.atomicProjection.atLeastOn(cmdResult.storeVersion).map { state =>
      state.projection.roleByName(roleName) match {
        case Some(role) => \/-(CreateRoleResponse(role.roleId))
        case None       => -\/(RoleBasedAuthorizationRequestModel.roleNotFoundAfterInsert.toResponseError)
      }
    }
}

object RoleBasedAuthorizationRequestModel {

  val permissionNotFoundAfterInsertErrorCode = 6001L
  val permissionNotFoundAfterInsert = EventSourceModelError(
    "After a successful insert the permission is not accessible.",
    EventSourceCommandError(permissionNotFoundAfterInsertErrorCode)
  )

  val roleNotFoundAfterInsertErrorCode = 6002L
  val roleNotFoundAfterInsert = EventSourceModelError(
    "After a successful insert the permission is not accessible.",
    EventSourceCommandError(roleNotFoundAfterInsertErrorCode)
  )

}

case class AddPermissionsToRoleRequest(roleID: RoleID, permissionsToAdd: Set[PermissionID])

case class AddPermissionsToRoleResponse()

case class DeletePermissionsFromRoleRequest(roleID: RoleID, permissionsToDelete: Set[PermissionID])

case class DeletePermissionsFromRoleResponse()

//Role
case class CreateRoleRequest(roleName: String)

case class CreateRoleResponse(roleID: RoleID)

case class DeleteRoleRequest(roleID: RoleID)

case class DeleteRoleResponse()

case class UpdateRoleNameRequest(roleID: RoleID, newRoleName: String)

case class UpdateRoleNameResponse()

case class GetRolesRequest()

case class GetRolesResponse(roles: Set[AccessRole])

//Permission
case class CreatePermissionRequest(code: String, description: String)

case class CreatePermissionResponse(permissionId: PermissionID)

case class UpdatePermissionDescriptionRequest(permissionId: PermissionID, description: String)

case class UpdatePermissionDescriptionResponse()

case class DeletePermissionRequest(permissionId: PermissionID)

case class DeletePermissionResponse()

case class GetPermissionsRequest()

case class GetPermissionsResponse(permissions: Set[Permission])

//Permission x Roles
case class GetRolesPermissionsRequest(roleID: Set[RoleID])

case class GetRolesPermissionsResponse(permissions: Map[RoleID, Set[PermissionID]])

