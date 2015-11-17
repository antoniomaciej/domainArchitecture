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

package eu.pmsoft.mcomponents.eventsourcing.inmemory

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreLoad

import scala.util.{ Failure, Success }

object LocalBindingInfrastructure {
  def create(): BindingInfrastructure = new LocalBindingInfrastructure()
}

class LocalBindingInfrastructure extends BindingInfrastructure {
  override def bind[E](projection: EventSourceProjection[E], eventStoreLoad: EventStoreLoad[E]): Unit = {
    new LocalProjectionEventStoreBinding(projection, eventStoreLoad).startBinding()
  }
}

class LocalProjectionEventStoreBinding[E](
    val projection:     EventSourceProjection[E],
    val eventStoreLoad: EventStoreLoad[E]
) {

  import scala.concurrent.ExecutionContext.Implicits.global

  def startBinding(): Unit = {
      def pushLoop(from: EventStoreVersion): Unit = {
        eventStoreLoad.loadEvents(EventStoreRange(from, None)).onComplete {
          case Failure(exception) => ???
          case Success(value) => {
            val last = value.foldLeft(from)((version, event) => {
              projection.projectEvent(event, version)
              version.add(1)
            })
            pushLoop(last)
          }
        }
      }

    pushLoop(projection.lastSnapshotVersion().add(1))

  }

  def handleEvent(event: E, version: EventStoreVersion): Unit = {
    projection.projectEvent(event, version)
  }

}
