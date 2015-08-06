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

package eu.pmsoft.domain.model.deploy

import akka.event.Logging
import eu.pmsoft.mcomponents.model.security.password.reset.mins._
import eu.pmsoft.mcomponents.reqres.ReqResDataModel.RequestResult
import spray.http.StatusCodes
import spray.httpx.marshalling.ToResponseMarshaller
import spray.routing._
import spray.util.LoggingContext

import scala.concurrent.ExecutionContext
import scalaz.{-\/, \/-}

trait UserManagementService extends HttpService with ApiDirectives {

  import JsonConfiguration._

  def api: PasswordResetApi

  implicit def executionContext: ExecutionContext

  private implicit def exceptionHandler(implicit log: LoggingContext):ExceptionHandler = ExceptionHandler {
    case e: Exception => complete(StatusCodes.InternalServerError)
  }

  private def completeApiCall[T](requestResult: RequestResult[T])(implicit marshaller: ToResponseMarshaller[T]): StandardRoute = {
    requestResult match {
      case -\/(a) => complete(StatusCodes.BadRequest, a)
      case \/-(b) => complete(b)
    }
  }

  val routingDefinition = (decompressRequest() & compressResponseIfRequested()) {
    pathPrefix("password") {
      logRequestResponse("password", Logging.DebugLevel) {
        path("init") {
          postJson {
            entity(as[InitializePasswordResetFlowRequest]) { init =>
              onSuccess(api.initializeFlow(init)) { res =>
                completeApiCall(res)
              }
            }
          }
        } ~
          path("cancel") {
            postJson {
              entity(as[CancelPasswordResetFlowRequest]) { cancel =>
                onSuccess(api.cancelFlow(cancel)) { res =>
                  completeApiCall(res)
                }
              }
            }
          } ~
          path("confirm") {
            postJson {
              entity(as[ConfirmPasswordResetFlowRequest]) { confirm =>
                onSuccess(api.confirmFlow(confirm)) { res =>
                  completeApiCall(res)
                }
              }
            }
          }
      }
    }
  }

}
