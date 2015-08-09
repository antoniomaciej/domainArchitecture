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

package eu.pmsoft.domain.model.deploy

import java.util.concurrent.TimeoutException

import eu.pmsoft.mcomponents.eventsourcing.{EventStoreVersion, EventSourceCommandConfirmation}
import eu.pmsoft.mcomponents.model.security.password.reset.mins._
import eu.pmsoft.mcomponents.reqres.ReqResDataModel._
import eu.pmsoft.mcomponents.reqres.{RequestErrorDomain, RequestErrorCode, ResponseError}

import scala.concurrent.Future
import scalaz.{-\/, \/-}

object MockApiConstanat {
  val testErrorCode = 101L
}

class MockApi extends PasswordResetApi {
  override def initializeFlow(req: InitializePasswordResetFlowRequest): Future[RequestResult[InitializePasswordResetFlowResponse]] =
    Future.successful(\/-(InitializePasswordResetFlowResponse(EventSourceCommandConfirmation(EventStoreVersion(0L)))))

  override def cancelFlow(req: CancelPasswordResetFlowRequest): Future[RequestResult[CancelPasswordResetFlowResponse]] =
    Future.successful(-\/(ResponseError(RequestErrorCode(MockApiConstanat.testErrorCode), RequestErrorDomain("domainTest"))))

  override def confirmFlow(req: ConfirmPasswordResetFlowRequest): Future[RequestResult[ConfirmPasswordResetFlowResponse]] =
    Future.failed(new TimeoutException("test timeout"))
}
