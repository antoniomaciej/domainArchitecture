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

package eu.pmsoft.mcomponents.model.security.password.reset

import eu.pmsoft.domain.model.SessionToken
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore._
import org.jasypt.util.password.rfc2307.RFC2307SSHAPasswordEncryptor

import scala.reflect._

object PasswordResetDomainModule {

  val eventStoreReference: EventStoreReference[PasswordResetDomain] =
    EventStoreReference[PasswordResetDomain](EventStoreID("PasswordResetDomain"), classTag[PasswordResetModelEvent], classTag[PasswordResetAggregate])

}

class PasswordResetDomainModule(implicit val eventSourcingConfiguration: EventSourcingConfiguration) extends DomainModule[PasswordResetDomain] {
  override lazy val logic: DomainLogic[PasswordResetDomain] = new PasswordResetModelLogicHandler()

  override lazy val schema: EventSerializationSchema[PasswordResetDomain] = new PasswordResetDomainEventSerializationSchema()

  override lazy val eventStore: EventStore[PasswordResetDomain] with VersionedEventStoreView[PasswordResetAggregate, PasswordResetModelState] =
    EventStoreProvider.createEventStore[PasswordResetDomain, PasswordResetModelStateInMemory](
      new PasswordResetEventStoreAtomicAtomicProjection(),
      schema,
      PasswordResetDomainModule.eventStoreReference
    )

  override lazy val sideEffects: PasswordResetModelSideEffects = new LocalThreadPasswordResetModelSideEffects()
}

class PasswordResetEventStoreAtomicAtomicProjection extends EventStoreAtomicProjectionCreationLogic[PasswordResetDomain, PasswordResetModelStateInMemory] {
  override def buildInitialState(): PasswordResetModelStateInMemory = PasswordResetModelStateInMemory()

  override def projectSingleEvent(
    state: PasswordResetModelStateInMemory,
    event: PasswordResetModelEvent
  ): PasswordResetModelStateInMemory =
    event match {
      case PasswordResetFlowCreated(userId, sessionToken, passwordResetToken) => state.created(userId, sessionToken, passwordResetToken)
      case PasswordResetFlowCancelled(userId, passwordResetToken)             => state.canceled(userId, passwordResetToken)
      case PasswordResetFlowConfirmed(userId, newPassword)                    => state.confirmed(userId, newPassword)
    }

}

class LocalThreadPasswordResetModelSideEffects extends PasswordResetModelSideEffects {

  val encryptor = new RFC2307SSHAPasswordEncryptor()

  override def generatePasswordResetToken(sessionToken: SessionToken): PasswordResetToken = {
    val secret = encryptor.encryptPassword(s"p:${sessionToken.token}")
    PasswordResetToken(token = secret)
  }

  override def validatePasswordResetToken(sessionToken: SessionToken, passwordResetToken: PasswordResetToken): Boolean = {
    encryptor.checkPassword(s"p:${sessionToken.token}", passwordResetToken.token)
  }
}

import eu.pmsoft.domain.model.{ SessionToken, UserID, UserPassword }
import monocle.macros.GenLens

import scala.language.{ higherKinds, postfixOps }

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
