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

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import eu.pmsoft.domain.model.{ SessionToken, UserID }
import eu.pmsoft.mcomponents.eventsourcing.{ EventStoreVersion, EventSourceCommandConfirmation }
import eu.pmsoft.mcomponents.minstance.ReqResDataModel.RequestResult
import eu.pmsoft.mcomponents.model.security.password.reset.UserIdFlowAggregate
import eu.pmsoft.mcomponents.model.security.password.reset.api._
import eu.pmsoft.mcomponents.model.security.roles.api.RoleBasedAuthorizationApi
import eu.pmsoft.mcomponents.model.user.registry.api.UserRegistrationApi
import eu.pmsoft.mcomponents.model.user.session.api.UserSessionApi
import eu.pmsoft.mcomponents.test.Mocked
import org.scalamock.MockFactoryBase
import org.scalamock.scalatest.MockFactory
import spray.can.Http

import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.duration._
import scalaz.\/-

object Boot extends App {
  implicit val system = ActorSystem("deploymentApp")
  val service = system.actorOf(Props[BindActorToRouting], "user-management-service")
  implicit val timeout = Timeout(5.seconds)
  val post8080 = 8080
  IO(Http) ? Http.Bind(service, interface = "localhost", port = post8080)
}

class BindActorToRouting extends Actor with UserManagementService {

  override def receive: Actor.Receive = runRoute(routingDefinition)

  override implicit def actorRefFactory: ActorContext = context

  override implicit def executionContext: ExecutionContext = context.dispatcher.prepare()

  override lazy val passwordResetApi: PasswordResetApi = new PasswordResetApi {
    override def initializeFlow(req: InitializePasswordResetFlowRequest): Future[RequestResult[InitializePasswordResetFlowResponse]] =
      Future.successful(\/-(InitializePasswordResetFlowResponse(EventSourceCommandConfirmation(EventStoreVersion.zero, UserIdFlowAggregate(UserID(0))))))

    override def cancelFlow(req: CancelPasswordResetFlowRequest): Future[RequestResult[CancelPasswordResetFlowResponse]] =
      Mocked.shouldNotBeCalled

    override def confirmFlow(req: ConfirmPasswordResetFlowRequest): Future[RequestResult[ConfirmPasswordResetFlowResponse]] =
      Mocked.shouldNotBeCalled
  }

}

