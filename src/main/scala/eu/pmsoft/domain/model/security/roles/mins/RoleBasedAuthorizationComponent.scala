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

import com.softwaremill.macwire._
import eu.pmsoft.domain.minstance._
import eu.pmsoft.domain.model.EventSourceDataModel._
import eu.pmsoft.domain.model._
import eu.pmsoft.domain.model.security.roles._
import eu.pmsoft.domain.model.security.roles.mins.RoleBasedAuthorizationDefinitions._

import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.std.scalaFuture._

trait RoleBasedAuthorizationComponent extends MicroComponent[RoleBasedAuthorizationApi] {
  override def providedContact: MicroComponentContract[RoleBasedAuthorizationApi] =
    MicroComponentModel.contractFor(RoleBasedAuthorizationApi.version, classOf[RoleBasedAuthorizationApi])

  def application: RoleBasedAuthorizationApplication

  override lazy val app: Future[RoleBasedAuthorizationApi] = Future.successful(new RoleBasedAuthorizationInternalInjector {
    override def module: RoleBasedAuthorizationApplication = application
  }.dispatcher)
}

trait RoleBasedAuthorizationInternalInjector {
  def module: RoleBasedAuthorizationApplication

  private implicit def internalExecutionContext: ExecutionContext = module.executionContext

  lazy val commandHandler = module.commandHandler
  lazy val projection = module.applicationContextProvider.contextStateAtomicProjection

  lazy val dispatcher = wire[RoleBasedAuthorizationRequestDispatcher]

}

class RoleBasedAuthorizationRequestDispatcher(val commandHandler: AsyncEventCommandHandler[RoleBasedAuthorizationModelCommand],
                                              val projection: AtomicEventStoreProjection[RoleBasedAuthorizationState])
                                             (implicit val executionContext: ExecutionContext)
  extends RoleBasedAuthorizationApi with RoleBasedAuthorizationExtractorFromProjection {

  override def createRole(req: CreateRoleRequest): Future[RequestResult[CreateRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(CreateRole(req.roleName)).map(_.asResponse))
    response <- EitherT(findRoleByName(cmdConfirmation, req.roleName))
  } yield response).run


  override def getRoles(req: GetRolesRequest): Future[RequestResult[GetRolesResponse]] =
    projection.lastSnapshot().map { state =>
      \/-(
        GetRolesResponse(state.allRoleId.map(state.roleById(_).get).toSet)
      )
    }

  override def getPermissions(req: GetPermissionsRequest): Future[RequestResult[GetPermissionsResponse]] =
    projection.lastSnapshot().map { state =>
      \/-(
        GetPermissionsResponse(state.allPermissionID.map(state.permissionById(_).get).toSet)
      )
    }


  override def deleteRole(req: DeleteRoleRequest): Future[RequestResult[DeleteRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(DeleteRole(req.roleID)).map(_.asResponse))
  } yield DeleteRoleResponse()).run

  override def updateRole(req: UpdateRoleNameRequest): Future[RequestResult[UpdateRoleNameResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(UpdateRoleName(req.roleID, req.newRoleName)).map(_.asResponse))
  } yield UpdateRoleNameResponse()).run

  override def updatePermission(req: UpdatePermissionDescriptionRequest): Future[RequestResult[UpdatePermissionDescriptionResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(UpdatePermissionDescription(req.permissionId, req.description)).map(_.asResponse))
  } yield UpdatePermissionDescriptionResponse()).run

  override def getRolesPermissions(req: GetRolesPermissionsRequest): Future[RequestResult[GetRolesPermissionsResponse]] =
    projection.lastSnapshot().map { state =>
      \/-(
        GetRolesPermissionsResponse(req.roleID.map { roleId => roleId -> state.getPermissionsForRole(roleId) }.toMap)
      )
    }

  override def createPermission(req: CreatePermissionRequest): Future[RequestResult[CreatePermissionResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(CreatePermission(req.code, req.description)).map(_.asResponse))
    response <- EitherT(findPermissionByName(cmdConfirmation, req.code))
  } yield response).run


  override def deletePermission(req: DeletePermissionRequest): Future[RequestResult[DeletePermissionResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(DeletePermission(req.permissionId)).map(_.asResponse))
  } yield DeletePermissionResponse()).run

  override def addPermissionToRole(req: AddPermissionsToRoleRequest): Future[RequestResult[AddPermissionsToRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(AddPermissionsToRole(req.permissionsToAdd, req.roleID)).map(_.asResponse))
  } yield AddPermissionsToRoleResponse()).run

  override def deletePermissionFromRole(req: DeletePermissionsFromRoleRequest): Future[RequestResult[DeletePermissionsFromRoleResponse]] = (for {
    cmdConfirmation <- EitherT(commandHandler.execute(DeletePermissionsFromRole(req.permissionsToDelete, req.roleID)).map(_.asResponse))
  } yield DeletePermissionsFromRoleResponse()).run
}

trait RoleBasedAuthorizationExtractorFromProjection {

  def projection: AtomicEventStoreProjection[RoleBasedAuthorizationState]

  implicit def executionContext: ExecutionContext

  def findPermissionByName(cmdResult: EventSourceCommandConfirmation, code: String):
  Future[RequestResult[CreatePermissionResponse]] =
    projection.atLeastOn(cmdResult.storeVersion).map { state =>
      state.permissionByCode(code) match {
        case Some(permission) => \/-(CreatePermissionResponse(permission.permissionId))
        case None => -\/(RoleBasedAuthorizationRequestModel.permissionNotFoundAfterInsert.toResponseError)
      }
    }

  //TODO. Create a projection on a exact store version to response according to the after command execution state
  def findRoleByName(cmdResult: EventSourceCommandConfirmation, roleName: String):
  Future[RequestResult[CreateRoleResponse]] =
    projection.atLeastOn(cmdResult.storeVersion).map { state =>
      state.roleByName(roleName) match {
        case Some(role) => \/-(CreateRoleResponse(role.roleId))
        case None => -\/(RoleBasedAuthorizationRequestModel.roleNotFoundAfterInsert.toResponseError)
      }
    }
}
