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

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import eu.pmsoft.domain.model.{SessionToken, UserID}
import eu.pmsoft.mcomponents.model.security.password.reset.mins.{InitializePasswordResetFlowRequest, InitializePasswordResetFlowResponse}
import spray.can.Http
import spray.client.pipelining._
import spray.http.BasicHttpCredentials
import spray.httpx.encoding.{Deflate, Gzip}
import spray.util._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TestBootClient extends App {
  implicit val system = ActorSystem("simple-spray-client")

  import system.dispatcher

  // execution context for futures below
  val log = Logging(system, getClass)

  import JsonConfiguration._

  log.info("sending request")

  val pipeline = (
    addHeader("X-My-Special-Header", "fancy-value")
      ~> addCredentials(BasicHttpCredentials("bob", "secret"))
      ~> encode(Gzip)
      ~> sendReceive
      ~> decode(Deflate)
      ~> unmarshal[InitializePasswordResetFlowResponse])

  val responseFuture = pipeline {
    Post("http://localhost:8080/password/init", InitializePasswordResetFlowRequest(UserID(0L), SessionToken("xxx")))
  }
  responseFuture onComplete {
    case Success(InitializePasswordResetFlowResponse(confirmation)) =>
      log.warning("Confirmation: '{}'.", confirmation)
      shutdown()
    case Success(somethingUnexpected) =>
      log.warning("something unexpected: '{}'.", somethingUnexpected)
      shutdown()
    case Failure(error) =>
      log.error(error, "Couldn't get elevation")
      shutdown()
  }

  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}
