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

package eu.pmsoft.mcomponents.model.user.session.inmemory

import eu.pmsoft.mcomponents.eventsourcing.inmemory.EventStoreWithProjectionInMemoryLogic
import eu.pmsoft.mcomponents.eventsourcing.{EventStoreID, EventStoreIdentification}
import eu.pmsoft.mcomponents.model.user.session._

import scala.reflect.{ClassTag, classTag}

class UserSessionEventStoreWithProjectionInMemoryLogic
  extends EventStoreWithProjectionInMemoryLogic[UserSessionEvent,
    UserSessionAggregate,
    UserSessionStateInMemory] {
  override def buildInitialState(): UserSessionStateInMemory = UserSessionStateInMemory()

  override def projectSingleEvent(state: UserSessionStateInMemory, event: UserSessionEvent):
  UserSessionStateInMemory = event match {
    case UserSessionCreated(sessionToken, userId) => state.createSession(sessionToken, userId)
    case UserSessionInvalidated(sessionToken, userId) => state.deleteSession(sessionToken, userId)
  }
}

class UserSessionEventStoreIdentification extends EventStoreIdentification[UserSessionEvent, UserSessionAggregate] {

  override def id: EventStoreID = EventStoreID("UserSessionStoreAndProjection")

  override def aggregateRootType: ClassTag[UserSessionAggregate] = classTag[UserSessionAggregate]

  override def rootEventType: ClassTag[UserSessionEvent] = classTag[UserSessionEvent]

}
