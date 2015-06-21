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
 */

package eu.pmsoft.domain.inmemory

import java.util.concurrent.atomic.AtomicReference

import eu.pmsoft.domain.model.EventSourceEngine._
import eu.pmsoft.domain.model._

import scala.concurrent.Future
import scalaz.\/-

abstract class AbstractAtomicEventStoreWithProjectionInMemory[E, S] extends AsyncEventStore[E] with AtomicEventStoreProjection[S] {

  protected[this] val inMemoryStore = new AtomicReference(AtomicEventStoreState[E, S](List[E](), buildInitialState()))

  def buildInitialState(): S

  def projectSingleEvent(state: S, event: E): S

  override def persistEvents(events: List[E]): Future[CommandResult] = {
    val currAtomicState = inMemoryStore.get

    val updatedEventHistory = currAtomicState.eventHistory ++ events
    val updatedStateProjection = (currAtomicState.state /: events)(projectSingleEvent)

    val updatedAtomicState = AtomicEventStoreState(updatedEventHistory, updatedStateProjection)
    if (!inMemoryStore.compareAndSet(currAtomicState, updatedAtomicState)) {
      throw new IllegalStateException("atomic update failed, there are some parallel operations on state")
    }
    val version = EventStoreVersion(updatedAtomicState.eventHistory.length)
    Future.successful(\/-(EventSourceCommandConfirmation(version)))
  }

  override def projection(): S = inMemoryStore.get().state
}

case class AtomicEventStoreState[E, S](eventHistory: List[E], state: S)

