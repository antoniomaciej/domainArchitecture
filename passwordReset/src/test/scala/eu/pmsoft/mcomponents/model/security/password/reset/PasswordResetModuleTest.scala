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
import eu.pmsoft.mcomponents.test.{BaseEventSourceSpec, CommandGenerator, GeneratedCommandSpecification}

abstract class PasswordResetModuleTest extends BaseEventSourceSpec with
GeneratedCommandSpecification[PasswordResetModelCommand, PasswordResetModelEvent,
  PasswordResetModelState, PasswordResetAggregate, PasswordResetApplication] {

  def infrastructure(): PasswordResetApplicationInfrastructure

  it should "reject invalid session tokens" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module).execute(InitializePasswordResetFlow(UserID(0), SessionToken("")))) { result =>
      result should be(-\/)
    }
  }

  it should "reject second password reset confirmation" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module)
      .execute(InitializePasswordResetFlow(UserID(0), SessionToken("validSessionToken")))) { result =>
      result should be(\/-)
      val process = stateProjection(module).lastSnapshot().futureValue.findFlowByUserID(UserID(0)).get
      whenReady(asyncCommandHandler(module)
        .execute(ConfirmPasswordResetFlow(process.sessionToken, process.passwordResetToken, UserPassword("newPassword")))) { confirmationResult =>
        confirmationResult should be(\/-)
        whenReady(asyncCommandHandler(module)
          .execute(ConfirmPasswordResetFlow(process.sessionToken, process.passwordResetToken, UserPassword("newPassword2")))) { confirmationResultTwo =>
          confirmationResultTwo should be(-\/)
        }
      }
    }
  }

  it should "reject confirmations with tokens pairs not matching security verification" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module)
      .execute(InitializePasswordResetFlow(UserID(0), SessionToken("validSessionToken")))) { result =>
      result should be(\/-)
      val process = stateProjection(module).lastSnapshot().futureValue.findFlowByUserID(UserID(0)).get
      whenReady(asyncCommandHandler(module)
        .execute(
          ConfirmPasswordResetFlow(
            SessionToken("validButNotMatching"),
            process.passwordResetToken,
            UserPassword("newPassword")))
      ) { confirmationResult =>
        confirmationResult should be(-\/)
      }
    }
  }

  it should "reject confirmation with invalid password" in {
    val module = createEmptyModule()
    whenReady(asyncCommandHandler(module)
      .execute(InitializePasswordResetFlow(UserID(0), SessionToken("validSessionToken")))) { result =>
      result should be(\/-)
      val process = stateProjection(module).lastSnapshot().futureValue.findFlowByUserID(UserID(0)).get
      whenReady(asyncCommandHandler(module)
        .execute(ConfirmPasswordResetFlow(process.sessionToken, process.passwordResetToken, UserPassword("")))) { confirmationResult =>
        confirmationResult should be(-\/)
      }
    }
  }


  override def createEmptyModule(): PasswordResetApplication = PasswordResetApplication.createApplication(infrastructure())

  override def buildGenerator(state: AtomicEventStoreView[PasswordResetModelState]):
  CommandGenerator[PasswordResetModelCommand] = new PasswordResetModelGenerator(state)

  override def postCommandValidation(state: PasswordResetModelState, command: PasswordResetModelCommand): Unit =
    command match {
      case InitializePasswordResetFlow(userId, sessionToken) =>
        state.findFlowByUserID(userId) should not be empty withClue ": Process initialized but not visible on state"
        val process = state.findFlowByUserID(userId).get
        process.sessionToken should be(sessionToken) withClue ": Process initialized but sessionToken do not match"
        state.findFlowByPasswordToken(process.passwordResetToken) should not be empty withClue ": Process started but passwordToken visible on state"
        state.findFlowByPasswordToken(process.passwordResetToken)
          .get should equal(process) withClue ": Process started but state by passwordToken do not match state by userId"
        state.getExistingProcessUserId should contain(userId) withClue ": UserId not marked as started"
      case CancelPasswordResetFlowByUser(userId) =>
        state.findFlowByUserID(userId) shouldBe empty withClue ": Process cancelled but visible on state"
        state.getExistingProcessUserId should not contain userId withClue ": UserId marked as started"
      case CancelPasswordResetFlowByToken(passwordResetToken) =>
        state.findFlowByPasswordToken(passwordResetToken) shouldBe empty withClue ": Process cancelled but passwordToken visible on state"
      case ConfirmPasswordResetFlow(sessionToken, passwordResetToken, newPassword) =>
    }

  override def validateState(state: PasswordResetModelState) {
    findInconsistentProcessByUserId(state) shouldBe empty withClue ": A userId marked as process started but the process is not found"
    findInconsistentProcessByPasswordToken(state) shouldBe empty withClue ": No process found for the provided passwordResetToken"
  }

  private def findInconsistentProcessByUserId(state: PasswordResetModelState): Option[UserID] = {
    state.getExistingProcessUserId
      .find(state.findFlowByUserID(_).isEmpty)
  }

  private def findInconsistentProcessByPasswordToken(state: PasswordResetModelState): Option[PasswordResetToken] = {
    state.getExistingProcessUserId
      .map(state.findFlowByUserID)
      .map(_.get)
      .map(_.passwordResetToken)
      .find(state.findFlowByPasswordToken(_).isEmpty)
  }
}
