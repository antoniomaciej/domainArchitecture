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

package eu.pmsoft.mcomponents.eventsourcing.atomic

import eu.pmsoft.mcomponents.eventsourcing.EventStoreVersion
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ Future, Promise }

trait LazyLoadByVersion[T] {

  def loadCurrentProjectionVersion(): VersionedProjection[T]

  private val futureValues = TrieMap[EventStoreVersion, Promise[VersionedProjection[T]]]()

  def atLeastOn(expectedStoreVersion: EventStoreVersion): Future[VersionedProjection[T]] = {
    val versionAndProjection = loadCurrentProjectionVersion()
    triggerDelayedViews(versionAndProjection)
    if (versionAndProjection.version.storeVersion >= expectedStoreVersion.storeVersion) {
      Future.successful(versionAndProjection)
    }
    else {
      createDelayedPromise(expectedStoreVersion)
    }
  }

  def triggerNewVersionAvailable(version: EventStoreVersion): Unit = {
    triggerDelayedViews(loadCurrentProjectionVersion())
  }
  def triggerNewVersionAvailable(versionedProjection: VersionedProjection[T]): Unit = {
    triggerDelayedViews(versionedProjection)
  }

  private def createDelayedPromise(expectedStoreVersion: EventStoreVersion): Future[VersionedProjection[T]] = {
    val promise = Promise[VersionedProjection[T]]()
    val futureProjection = futureValues.putIfAbsent(expectedStoreVersion, promise) match {
      case Some(oldPromise) => oldPromise.future
      case None             => promise.future
    }
    futureProjection
  }

  private def triggerDelayedViews(versionAndProjection: VersionedProjection[T]): Unit = {
    futureValues.filterKeys {
      _.storeVersion <= versionAndProjection.version.storeVersion
    } foreach { pair =>
      pair._2.trySuccess(versionAndProjection)
    }
    val toRemove = futureValues.filter(_._2.isCompleted)
    toRemove.foreach {
      case (key, promise) => futureValues.remove(key, promise)
    }
  }

}
