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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry.test

import java.util.concurrent.atomic.AtomicInteger

import eu.pmsoft.domain.model.CommandGenerator
import eu.pmsoft.mcomponents.eventsourcing.AtomicEventStoreProjection
import eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry._
import org.scalacheck.Gen._
import org.scalacheck._

import scala.language.postfixOps

class TestUserRegistrationGenerators(val state: AtomicEventStoreProjection[TestUserRegistrationState]) extends
CommandGenerator[TestUserRegistrationCommand] {

  private lazy val minimumTextLen = 5
  private lazy val maximumTextLen = 30
  private lazy val passwordLen = 20
  //Generators that depend on the model state as provided by the query api
  val genExistingUid = Gen.wrap(
    Gen.oneOf(loadAllUID())
  )
  val noEmptyTextString = for {
    size <- choose(minimumTextLen, maximumTextLen)
    chars <- listOfN(size, Gen.alphaNumChar)
  } yield chars.mkString
  val userNameCounter = new AtomicInteger(0)
  val genUniqueUserName = Gen.wrap(Gen.const(nextUserName))
  val genEmail = for {
    user <- genUniqueUserName
  } yield TestUserLogin(s"$user@test.domain.com")
  val genPassword = for {
    chars <- listOfN(passwordLen, Gen.alphaNumChar)
  } yield TestUserPassword(chars.mkString)
  //Commands
  val genAddUser = for {
    email <- genEmail
    password <- genPassword
  } yield TestAddUser(email, password)
  val genUpdateUser = for {
    uid <- genExistingUid
    password <- genPassword
  } yield TestUpdateUserPassword(uid, password)
  val genUpdateUserRoles = for {
    uid <- genExistingUid
    password <- Gen.containerOf[Set, TestRoleID](Gen.oneOf((0 to 6).map(TestRoleID(_))))
  } yield TestUpdateUserRoles(uid, password)
  val genUpdateActiveUserStatus = for {
    uid <- genExistingUid
    status <- Gen.oneOf(true, false)
  } yield TestUpdateActiveUserStatus(uid, status)

  def loadAllUID(): Seq[TestUserID] = state.lastSnapshot().futureValue.getAllUid.toSeq

  override def generateSingleCommands: Gen[TestUserRegistrationCommand] = Gen.frequency(
    (1, genAddUser),
    (3, genUpdateUser),
    (3, genUpdateActiveUserStatus),
    (10, genUpdateUserRoles)
  )

  override def generateWarmUpCommands: Gen[List[TestUserRegistrationCommand]] = Gen.nonEmptyListOf[TestUserRegistrationCommand](genAddUser)

  private def nextUserName = "userName" + userNameCounter.getAndAdd(1)

}
