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
 */

package eu.pmsoft.domain.inmemory.security.password.reset

import com.softwaremill.macwire._
import eu.pmsoft.domain.inmemory.AbstractAtomicEventStoreWithProjectionInMemory
import eu.pmsoft.domain.model.security.password.reset._
import eu.pmsoft.domain.model.userRegistry.{UserID, UserPassword}
import eu.pmsoft.domain.model.{AsyncEventHandlingModule, AtomicEventStoreProjection, DomainLogicAsyncEventCommandHandler}

import scala.concurrent.ExecutionContext

trait PasswordResetInMemoryModule extends
AsyncEventHandlingModule[PasswordResetModelCommand,
  PasswordResetModelEvent,
  PasswordResetModelState] {

  lazy val localSideEffects = wire[LocalThreadPasswordResetModelSideEffects]
  lazy val logic = wire[PasswordResetModelLogicHandler]

  lazy val state = wire[PasswordResetModelStateProjection]
  lazy val commandHandler = wire[PasswordResetInMemoryModuleCommandHandler]
}

class PasswordResetInMemoryModuleCommandHandler(val logic: PasswordResetModelLogicHandler,
                                                val store: PasswordResetModelStateProjection,
                                                implicit val executionContext: ExecutionContext)
  extends DomainLogicAsyncEventCommandHandler[PasswordResetModelCommand,
    PasswordResetModelEvent,
    PasswordResetModelState] {
  override protected def atomicProjection: AtomicEventStoreProjection[PasswordResetModelState] = store
}

//Insecure implementation for test propose only
class LocalThreadPasswordResetModelSideEffects extends PasswordResetModelSideEffects {
  override def generatePasswordResetToken(sessionToken: SessionToken): PasswordResetToken =
    PasswordResetToken(token = "p:" + sessionToken.token)

  override def validatePasswordResetToken(sessionToken: SessionToken, passwordResetToken: PasswordResetToken): Boolean =
    passwordResetToken.token == "p:" + sessionToken.token
}

class PasswordResetModelStateProjection extends
AbstractAtomicEventStoreWithProjectionInMemory[PasswordResetModelEvent,
  PasswordResetModelStateInMemory] {
  override def buildInitialState(): PasswordResetModelStateInMemory = PasswordResetModelStateInMemory()

  override def projectSingleEvent(state: PasswordResetModelStateInMemory,
                                  event: PasswordResetModelEvent): PasswordResetModelStateInMemory =
    event match {
      case PasswordResetFlowCreated(userId, sessionToken, passwordResetToken) =>
        state.created(userId, sessionToken, passwordResetToken)
      case PasswordResetFlowCancelled(userId, passwordResetToken) =>
        state.canceled(userId, passwordResetToken)
      case PasswordResetFlowConfirmed(userId, newPassword) =>
        state.confirmed(userId, newPassword)
    }

}


import monocle.macros.GenLens

import scala.language.{higherKinds, postfixOps}


object PasswordResetModelStateInMemoryLenses {
  val stateLens = GenLens[PasswordResetModelStateInMemory]
  val _startedProcesses = stateLens(_.startedProcesses)
}

case class PasswordResetModelStateInMemory(startedProcesses: Set[PasswordResetFlowStatus] = Set())
  extends PasswordResetModelState {

  import PasswordResetModelStateInMemoryLenses._

  def confirmed(userId: UserID, newPassword: UserPassword): PasswordResetModelStateInMemory =
    _startedProcesses
      .modify(_.filterNot { p => p.userId == userId })(this)

  def canceled(userId: UserID, passwordResetToken: PasswordResetToken): PasswordResetModelStateInMemory =
    _startedProcesses
      .modify(_.filterNot { p => p.userId == userId || p.passwordResetToken == passwordResetToken })(this)

  def created(userId: UserID, sessionToken: SessionToken,
              passwordResetToken: PasswordResetToken): PasswordResetModelStateInMemory =
    _startedProcesses
      .modify(_ + PasswordResetFlowStatus(userId, sessionToken, passwordResetToken))(this)

  override def findFlowByUserID(userId: UserID): Option[PasswordResetFlowStatus] =
    startedProcesses.find(_.userId == userId)

  override def findFlowByPasswordToken(passwordResetToken: PasswordResetToken): Option[PasswordResetFlowStatus] =
    startedProcesses.find(_.passwordResetToken == passwordResetToken)

  override def getExistingProcessUserId: Stream[UserID] = startedProcesses.map(_.userId).toStream
}
