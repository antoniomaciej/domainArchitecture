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

package eu.pmsoft.mcomponents.eventsourcing.projection

import eu.pmsoft.mcomponents.eventsourcing.atomic.{ LazyLoadByVersion, Atomic }
import eu.pmsoft.mcomponents.eventsourcing.{ VersionedEvent, EventStoreVersion, BindingInfrastructure, DomainSpecification }
import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreReference
import rx.{ Subscriber, Observable }

import scala.concurrent.Future

object InMemoryProjections {

  def bindProjection[D <: DomainSpecification, P](
    eventStoreReference: EventStoreReference[D],
    buildLogic:          EventSourceProjectionCreationLogic[D, P],
    binding:             BindingInfrastructure
  ): EventSourceProjectionView[P] = {
    val eventStream: Observable[VersionedEvent[D]] = binding.consumerApi.eventStoreStream[D](eventStoreReference, EventStoreVersion.zero)
    val projection = new InMemoryProjectionAtomic(buildLogic)
    eventStream.subscribe(projection)
    projection
  }
}

private class InMemoryProjectionAtomic[D <: DomainSpecification, P](buildLogic: EventSourceProjectionCreationLogic[D, P])
    extends Subscriber[VersionedEvent[D]] with EventSourceProjectionView[P] with LazyLoadByVersion[P] {
  private val state: Atomic[VersionedProjection[P]] = Atomic(VersionedProjection(EventStoreVersion.zero, buildLogic.zero()))

  override def getProjectionView(expectedVersion: EventStoreVersion): Future[VersionedProjection[P]] = atLeastOn(expectedVersion)

  override def onError(e: Throwable): Unit = {
    //TODO a central logic should handle errors at this level
  }

  override def onCompleted(): Unit = {}

  override def onNext(versionAndEvent: VersionedEvent[D]): Unit = {
    val updated: VersionedProjection[P] = state.updateAndGet { state =>
      VersionedProjection(versionAndEvent.version, buildLogic.projectEvent(state.projection, versionAndEvent.version, versionAndEvent.event))
    }
    triggerNewVersionAvailable(updated)
  }

  override def getLastSnapshotView(): VersionedProjection[P] = state()

  override def getCurrentVersion(): VersionedProjection[P] = state()
}

