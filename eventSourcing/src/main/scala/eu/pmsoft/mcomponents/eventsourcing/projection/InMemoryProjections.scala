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
