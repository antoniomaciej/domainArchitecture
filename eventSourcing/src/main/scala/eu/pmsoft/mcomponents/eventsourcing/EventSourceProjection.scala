package eu.pmsoft.mcomponents.eventsourcing

trait EventSourceProjection[E] {

  def projectEvent(event: E, storeVersion: EventStoreVersion): Unit

  def lastSnapshotVersion(): EventStoreVersion
}
