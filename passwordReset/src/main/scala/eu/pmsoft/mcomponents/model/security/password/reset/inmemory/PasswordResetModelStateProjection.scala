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

package eu.pmsoft.mcomponents.model.security.password.reset.inmemory

import eu.pmsoft.mcomponents.eventsourcing.inmemory.EventStoreWithProjectionInMemoryLogic
import eu.pmsoft.mcomponents.eventsourcing.{EventStoreID, EventStoreIdentification}
import eu.pmsoft.mcomponents.model.security.password.reset._

import scala.reflect.{ClassTag, classTag}


class PasswordResetEventStoreWithProjectionInMemoryLogic extends EventStoreWithProjectionInMemoryLogic[PasswordResetModelEvent,
  PasswordResetAggregate,
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

class PasswordResetEventStoreIdentification extends EventStoreIdentification[PasswordResetModelEvent,
  PasswordResetAggregate] {

  override def id: EventStoreID = EventStoreID("PasswordResetModelStateProjection")

  override def aggregateRootType: ClassTag[PasswordResetAggregate] = classTag[PasswordResetAggregate]

  override def rootEventType: ClassTag[PasswordResetModelEvent] = classTag[PasswordResetModelEvent]

}
