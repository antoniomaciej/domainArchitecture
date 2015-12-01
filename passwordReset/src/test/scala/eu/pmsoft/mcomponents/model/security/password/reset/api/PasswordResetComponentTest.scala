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

package eu.pmsoft.mcomponents.model.security.password.reset.api

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.model.security.password.reset._
import eu.pmsoft.mcomponents.test.BaseEventSourceComponentTestSpec

import scala.concurrent.ExecutionContext

class PasswordResetComponentTest extends BaseEventSourceComponentTestSpec {

  implicit val eventSourceConfiguration = EventSourcingConfiguration(
    ExecutionContext.Implicits.global,
    LocalBindingInfrastructure.create(),
    Set(
      EventStoreInMemory(PasswordResetDomainModule.eventStoreReference)
    )
  )

  it should "start a reset password flow and cancel" in {
    //Given
    val module = createModule()
    val api = module.passwordResetApi
    val app = module.cmdApi
    //When
    val initCall = api.initializeFlow(
      InitializePasswordResetFlowRequest(UserID(0L), SessionToken("sessionToken"))
    ).futureValue
    initCall should be(\/-)

    //Then
    val state = app.atomicProjection.lastSnapshot()
    val processOp = state.findFlowByUserID(UserID(0L))
    processOp should not be empty
    val process = processOp.get

    api.cancelFlow(CancelPasswordResetFlowRequest(process.passwordResetToken)).futureValue should be(\/-)
  }
  it should "start a reset password flow and confirm" in {
    //Given
    val module = createModule()
    val api = module.passwordResetApi
    val app = module.cmdApi
    //When
    val initCall = api.initializeFlow(
      InitializePasswordResetFlowRequest(UserID(0L), SessionToken("sessionToken"))
    ).futureValue
    initCall should be(\/-)

    //Then
    val state = app.atomicProjection.lastSnapshot()
    val processOp = state.findFlowByUserID(UserID(0L))
    processOp should not be empty
    val process = processOp.get

    api.confirmFlow(ConfirmPasswordResetFlowRequest(
      SessionToken("sessionToken"),
      process.passwordResetToken,
      UserPassword("Changed")
    )).futureValue should be(\/-)

  }

  def createModule(): PasswordResetModule = {
    implicit val eventSourceExecutionContext = EventSourceExecutionContextProvider.create()
    val application: DomainCommandApi[PasswordResetDomain] = eventSourceExecutionContext.assemblyDomainApplication(new PasswordResetDomainModule())
    new PasswordResetModule {
      override lazy val cmdApi: DomainCommandApi[PasswordResetDomain] = application
    }
  }
}
