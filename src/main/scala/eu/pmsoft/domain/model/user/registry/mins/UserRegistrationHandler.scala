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

package eu.pmsoft.domain.model.user.registry.mins

import eu.pmsoft.domain.model.EventSourceDataModel._
import eu.pmsoft.domain.model._
import eu.pmsoft.domain.model.user.registry.UserRegistrationApplicationDefinitions._
import eu.pmsoft.domain.model.user.registry._

import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.std.scalaFuture._

class UserRegistrationHandler(val registrationState: OrderedEventStoreProjector[UserRegistrationState],
                              val commandHandler: AsyncEventCommandHandler[UserRegistrationCommand])
                             (implicit val executionContext: ExecutionContext)
  extends RequestHandler[RegisterUserRequest, RegisterUserResponse] {


  override def handle(request: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]] = (for {
    cmdResult <- EitherT(commandHandler.execute(AddUser(request.login, request.passwordHash)).map(_.asResponse))
    response <- EitherT(findCreatedUser(cmdResult, request.login))
  } yield response).run


  def findCreatedUser(cmdResult: EventSourceCommandConfirmation, login: UserLogin): Future[RequestResult[RegisterUserResponse]] =
    registrationState.atLeastOn(cmdResult.storeVersion).map { state =>
      state.getUserByLogin(login) match {
        case Some(user) => \/-(RegisterUserResponse(user.uid))
        case None => -\/(UserRegistrationRequestModel.criticalUserNotFoundAfterSuccessRegistration.toResponseError)
      }
    }

}
