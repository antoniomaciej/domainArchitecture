package eu.pmsoft.mcomponents.eventsourcing.atomic

import eu.pmsoft.mcomponents.eventsourcing.EventStoreVersion
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ Future, Promise }

trait LazyLoadByVersion[T] {

  def getCurrentVersion(): VersionedProjection[T]

  private val futureValues = TrieMap[EventStoreVersion, Promise[VersionedProjection[T]]]()

  def atLeastOn(expectedStoreVersion: EventStoreVersion): Future[VersionedProjection[T]] = {
    val versionAndProjection = getCurrentVersion()
    triggerDelayedViews(versionAndProjection)
    if (versionAndProjection.version.storeVersion >= expectedStoreVersion.storeVersion) {
      Future.successful(versionAndProjection)
    }
    else {
      createDelayedPromise(expectedStoreVersion)
    }
  }

  def triggerNewVersionAvailable(version: EventStoreVersion): Unit = {
    triggerDelayedViews(getCurrentVersion())
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
