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

import com.softwaremill.macwire._
import eu.pmsoft.domain.model.UserLogin
import eu.pmsoft.domain.model.user.registry.mins.UserRegistrationApplicationDefinitions._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.minstance.{MicroComponentModel, MicroComponentContract, MicroComponent}
import eu.pmsoft.mcomponents.model.user.registry._
import eu.pmsoft.mcomponents.reqres.ReqResDataModel
import eu.pmsoft.mcomponents.reqres.ReqResDataModel._

import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.std.scalaFuture._


trait UserRegistrationComponent extends MicroComponent[UserRegistrationApi] {
  override def providedContact: MicroComponentContract[UserRegistrationApi] =
    MicroComponentModel.contractFor(UserRegistrationApi.version, classOf[UserRegistrationApi])

  def applicationModule: UserRegistrationApplication

  override lazy val app: Future[UserRegistrationApi] = Future.successful(new UserRegistrationInternalInjector {

    override lazy val module: UserRegistrationApplication = applicationModule
  }.app)
}

trait UserRegistrationInternalInjector {
  def module: UserRegistrationApplication

  private implicit def executionContextInternal: ExecutionContext = module.executionContext

  lazy val commandHandler = module.commandHandler

  lazy val projection = module.applicationContextProvider.contextStateAtomicProjection
  lazy val app = wire[UserRegistrationRequestDispatcher]

}

class UserRegistrationRequestDispatcher(val registrationState: AtomicEventStoreProjection[UserRegistrationState],
                                        val commandHandler: AsyncEventCommandHandler[UserRegistrationCommand])
                                       (implicit val executionContext: ExecutionContext)
  extends UserRegistrationApi {

  override def findRegisteredUser(searchForUser: SearchForUserIdRequest): Future[RequestResult[SearchForUserIdResponse]] =
    registrationState.lastSnapshot().map { state =>
      state.getUserByLogin(searchForUser.login).filter(_.passwordHash == searchForUser.passwordHash) match {
        case Some(user) => \/-(SearchForUserIdResponse(user.uid))
        case None => -\/(UserRegistrationRequestModel.userIdNotFound.toResponseError)
      }
    }

  override def registerUser(registrationRequest: RegisterUserRequest): Future[RequestResult[RegisterUserResponse]] =
    (for {
      cmdResult <- EitherT(commandHandler.execute(AddUser(registrationRequest.login, registrationRequest.passwordHash)).map(_.asResponse))
      response <- EitherT(findCreatedUser(cmdResult, registrationRequest.login))
    } yield response).run

  private def findCreatedUser(cmdResult: EventSourceCommandConfirmation, login: UserLogin): Future[RequestResult[RegisterUserResponse]] =
    registrationState.atLeastOn(cmdResult.storeVersion).map { state =>
      state.getUserByLogin(login) match {
        case Some(user) => \/-(RegisterUserResponse(user.uid))
        case None => -\/(UserRegistrationRequestModel.criticalUserNotFoundAfterSuccessRegistration.toResponseError)
      }
    }

}
