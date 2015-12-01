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

package eu.pmsoft.mcomponents.model.security.password.reset

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.AtomicEventStoreView
import eu.pmsoft.mcomponents.test.CommandGenerator
import org.scalacheck.Gen
import org.scalacheck.Gen._

class PasswordResetModelGenerator(val state: AtomicEventStoreView[PasswordResetModelState])
    extends CommandGenerator[PasswordResetModelCommand] {

  lazy val genInitializePasswordResetFlow = for {
    userId <- genNewUserId
    sessionToken <- genSessionToken
  } yield InitializePasswordResetFlow(userId, sessionToken)
  lazy val genCancelPasswordResetFlowByUser = for {
    existingUserId <- genExistingUserId
  } yield CancelPasswordResetFlowByUser(existingUserId)
  lazy val genCancelPasswordResetFlowByToken = for {
    process <- genExistingProcess
  } yield CancelPasswordResetFlowByToken(process.passwordResetToken)
  lazy val genConfirmPasswordResetFlow = for {
    process <- genExistingProcess
    newPassword <- genValidPassword
  } yield ConfirmPasswordResetFlow(process.sessionToken, process.passwordResetToken, newPassword)
  lazy val fixSizeText = for {
    chars <- listOfN(tokenTextLen, Gen.alphaNumChar)
  } yield chars.mkString
  lazy val genValidPassword = for {
    password <- fixSizeText
  } yield UserPassword(password)
  lazy val genSessionToken = for {
    token <- genToken
  } yield SessionToken(token)
  lazy val genToken = fixSizeText
  val nrOfInitialProcessToCreate = 4
  val minUserId = 0L
  val maxUserId = 100L
  //Stateless generators
  val tokenTextLen = 20

  override def generateSingleCommands: Gen[PasswordResetModelCommand] = Gen.frequency(
    (4, genInitializePasswordResetFlow),
    (1, genCancelPasswordResetFlowByUser),
    (1, genCancelPasswordResetFlowByToken),
    (2, genConfirmPasswordResetFlow)
  )

  override def generateWarmUpCommands: Gen[List[PasswordResetModelCommand]] = Gen.listOfN(nrOfInitialProcessToCreate, genInitializePasswordResetFlow)

  private def genNewUserId = for {
    active <- Gen.wrap(Gen.const(state.lastSnapshot().getExistingProcessUserId.map(_.id).toSet))
    userId <- Gen.oneOf(((minUserId to maxUserId).toSet[Long] -- active).toSeq)
  } yield UserID(userId)

  private def genExistingUserId = Gen.wrap(
    Gen.oneOf(state.lastSnapshot().getExistingProcessUserId.toSeq)
  )

  private def genExistingProcess = Gen.wrap(
    Gen.oneOf(state.lastSnapshot().getExistingProcessUserId
      .map(
        state.lastSnapshot().findFlowByUserID(_).get
      )
      .toSeq)
  )
}
