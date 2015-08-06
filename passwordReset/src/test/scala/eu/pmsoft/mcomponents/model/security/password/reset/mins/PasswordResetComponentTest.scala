/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Pawe? Cesar Sanjuan Szklarz
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

package eu.pmsoft.mcomponents.model.security.password.reset.mins

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.minstance.{ApiContract, MicroComponentRegistry}
import eu.pmsoft.mcomponents.model.security.password.reset.PasswordResetApplication
import eu.pmsoft.mcomponents.model.security.password.reset.inmemory.PasswordResetInMemoryApplication

import scala.concurrent.ExecutionContext

class PasswordResetComponentTest extends ComponentSpec {

  //TODO add a projection to validate state changes

  import scala.concurrent.ExecutionContext.Implicits.global

  it should "start a reset password flow and cancel" in {
    val app: PasswordResetInMemoryApplication = new PasswordResetInMemoryApplication()

    val api = createComponent(app)
    val initCall = api.initializeFlow(
      InitializePasswordResetFlowRequest(UserID(0L), SessionToken("sessionToken"))
    ).futureValue
    initCall should be(\/-)

    val state = app.applicationContextProvider.contextStateAtomicProjection.lastSnapshot().futureValue
    val processOp = state.findFlowByUserID(UserID(0L))
    processOp should not be empty
    val process = processOp.get

    api.cancelFlow(CancelPasswordResetFlowRequest(process.passwordResetToken)).futureValue should be(\/-)

  }
  it should "start a reset password flow and confirm" in {
    val app: PasswordResetInMemoryApplication = new PasswordResetInMemoryApplication()

    val api = createComponent(app)
    val initCall = api.initializeFlow(
      InitializePasswordResetFlowRequest(UserID(0L), SessionToken("sessionToken"))
    ).futureValue
    initCall should be(\/-)

    val state = app.applicationContextProvider.contextStateAtomicProjection.lastSnapshot().futureValue
    val processOp = state.findFlowByUserID(UserID(0L))
    processOp should not be empty
    val process = processOp.get

    api.confirmFlow(ConfirmPasswordResetFlowRequest(
      SessionToken("sessionToken"),
      process.passwordResetToken,
      UserPassword("Changed"))).futureValue should be(\/-)

  }

  def createComponent(testApp: PasswordResetInMemoryApplication): PasswordResetApi = {
    val registry = MicroComponentRegistry.create()

    val roleAuth = new PasswordResetComponent {
      override lazy val application: PasswordResetApplication = testApp
      override implicit lazy val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    }

    registry.registerComponent(roleAuth)
    registry.initializeInstances().futureValue shouldBe \/-
    registry.bindComponent(
      ApiContract(classOf[PasswordResetApi])
    ).futureValue
  }
}
