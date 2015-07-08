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

package eu.pmsoft.domain.model.security

import eu.pmsoft.domain.model.security.roles._
import eu.pmsoft.domain.model.{AtomicEventStoreProjection, CommandGenerator}
import org.scalacheck.Gen
import org.scalacheck.Gen._

class RoleBasedAuthorizationGenerators(val state: AtomicEventStoreProjection[RoleBasedAuthorizationState]) extends
CommandGenerator[RoleBasedAuthorizationModelCommand] {

  override def generateSingleCommands: Gen[RoleBasedAuthorizationModelCommand] = Gen.frequency(
    (1, genDeleteRole),
    (2, genCreateRole),
    (3, genCreatePermission),
    (2, genDeletePermission),
    (2, genUpdatePermissionDescription),
    (2, genUpdateRoleName),
    (10, genAddPermissionToRole),
    (10, genDeletePermissionToRole)
  )

  override def generateWarmUpCommands: Gen[List[RoleBasedAuthorizationModelCommand]] = Gen.oneOf(roleAndPermission, permissionAndRole)

  lazy val roleAndPermission = for {
    addRole <- genCreateRole
    addPermission <- genCreatePermission
  } yield List(addPermission, addRole)

  lazy val permissionAndRole = for {
    addRole <- genCreateRole
    addPermission <- genCreatePermission
  } yield List(addRole, addPermission)

  //Generators that depend on the model state as provided by the query api
  lazy val genExistingRoleId = Gen.wrap(
    Gen.oneOf(state.lastSnapshot().allRoleId)
  )
  lazy val genExistingPermissionId = Gen.wrap(
    Gen.oneOf(state.lastSnapshot().allPermissionID)
  )
  lazy val genExistingPermissionSetId = Gen.wrap(
    Gen.someOf(state.lastSnapshot().allPermissionID).map(_.toSet)
  )

  def genExistingPermissionSetFromRole(roleID: RoleID): Gen[Set[PermissionID]] = Gen.wrap(
    Gen.someOf(state.lastSnapshot().getPermissionsForRole(roleID)).map( _.toSet)
  )

  private val minimumTextLen = 5
  private val maximumTextLen = 30
  //Stateless generators
  lazy val noEmptyTextString = for {
    size <- choose(minimumTextLen, maximumTextLen)
    chars <- listOfN(size, Gen.alphaNumChar)
  } yield chars.mkString

  lazy val genDeletePermission = for {
    permissionId <- genExistingPermissionId
  } yield DeletePermission(permissionId)

  lazy val genCreatePermission = for {
    name <- noEmptyTextString
    description <- noEmptyTextString
  } yield CreatePermission(name, description)

  lazy val genDeleteRole = for {
    roleID <- genExistingRoleId
  } yield DeleteRole(roleID)

  lazy val genCreateRole = for {
    name <- noEmptyTextString
  } yield CreateRole(name)

  lazy val genAddPermissionToRole = for {
    roleID <- genExistingRoleId
    permissionSetId <- genExistingPermissionSetId
  } yield AddPermissionsToRole(permissionSetId, roleID)

  lazy val genDeletePermissionToRole = for {
    roleID <- genExistingRoleId
    permissionSetId <- genExistingPermissionSetFromRole(roleID)
  } yield DeletePermissionsFromRole(permissionSetId, roleID)


  lazy val genUpdateRoleName = for {
    roleID <- genExistingRoleId
    name <- noEmptyTextString
  } yield UpdateRoleName(roleID, name)

  lazy val genUpdatePermissionDescription = for {
    permissionId <- genExistingPermissionId
    description <- noEmptyTextString
  } yield UpdatePermissionDescription(permissionId, description)

}
