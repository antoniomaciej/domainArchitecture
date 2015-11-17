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

import akka.actor.ActorSystem
import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.eventsourcing.{ EventSourceCommandConfirmation, EventStoreVersion }
import eu.pmsoft.mcomponents.minstance.{ RequestErrorCode, RequestErrorDomain, ResponseError }
import eu.pmsoft.mcomponents.model.security.password.reset._
import eu.pmsoft.mcomponents.model.security.password.reset.mins._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers, OneInstancePerTest }
import spray.http.HttpEncodings
import spray.http.HttpHeaders.`Accept-Encoding`
import spray.http.StatusCodes._
import spray.httpx.ResponseTransformation
import spray.httpx.encoding.Gzip
import spray.httpx.unmarshalling._
import spray.testkit.ScalatestRouteTest

import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ -\/, \/- }

class UserManagementServiceTest
    extends FlatSpec
    with Matchers
    with ScalatestRouteTest
    with UserManagementService
    with ResponseTransformation
    with OneInstancePerTest
    with MockFactory {

  def actorRefFactory: ActorSystem = system

  override implicit def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override lazy val passwordResetApi: PasswordResetApi = mock[PasswordResetApi]

  import JsonConfiguration._

  it should "Receive call backend api" in {
    val apiCall = InitializePasswordResetFlowRequest(UserID(0L), SessionToken("xxx"))
    passwordResetApi.initializeFlow _ expects apiCall returning Future.successful(\/-(InitializePasswordResetFlowResponse(EventSourceCommandConfirmation(EventStoreVersion(0L)))))
    Post("/password/init", apiCall) ~> routingDefinition ~> check {
      val res = responseAs[InitializePasswordResetFlowResponse]
      res.confirmation.storeVersion.storeVersion should be(0L)
    }
  }

  it should "Receive call backend api - with gzip encoding" in {
    val apiCall = InitializePasswordResetFlowRequest(UserID(1L), SessionToken("yyy"))
    passwordResetApi.initializeFlow _ expects apiCall returning Future.successful(\/-(InitializePasswordResetFlowResponse(EventSourceCommandConfirmation(EventStoreVersion(1L)))))

    Post("/password/init", apiCall) ~> `Accept-Encoding`(HttpEncodings.gzip) ~> routingDefinition ~> check {
      val res = Gzip.decode(response).as[InitializePasswordResetFlowResponse].right.get
      res.confirmation.storeVersion.storeVersion should be(1L)
    }
  }

  it should "Handler errors from api" in {
    val testErrorCode = 101L
    val cancelApiCall = CancelPasswordResetFlowRequest(PasswordResetToken("xxx"))
    passwordResetApi.cancelFlow _ expects cancelApiCall returning Future.successful(-\/(ResponseError(RequestErrorCode(testErrorCode), RequestErrorDomain("domainTest"))))
    Post("/password/cancel", cancelApiCall) ~> routingDefinition ~> check {
      status should be(BadRequest)
      val res = responseAs[ResponseError]
      res.errorCode.code should be(testErrorCode)
      res.domain.domain should be("domainTest")
    }
  }

  it should "Handler errors from execution infrastructure as timeouts" in {
    val confirmApiCall = ConfirmPasswordResetFlowRequest(SessionToken("session"), PasswordResetToken("token"), UserPassword("newP"))
    passwordResetApi.confirmFlow _ expects confirmApiCall returning Future.failed(new TimeoutException("test timeout"))
    Post("/password/confirm", confirmApiCall) ~> routingDefinition ~> check {
      status should be(InternalServerError)
    }
  }

}
