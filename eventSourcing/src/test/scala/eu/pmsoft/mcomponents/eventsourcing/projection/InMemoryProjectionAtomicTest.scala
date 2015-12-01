package eu.pmsoft.mcomponents.eventsourcing.projection

import eu.pmsoft.mcomponents.eventsourcing.EventStoreVersion
import org.scalatest.{ FlatSpec, Matchers }
import rx.Observable

class InMemoryProjectionAtomicTest extends FlatSpec with Matchers {

  it should "ignore errors of the events stream" in {

    //given
    val projector = new InMemoryProjectionAtomic(new TestProjection())

    //when
    val errorStream = Observable.error(new IllegalStateException())
    errorStream.subscribe(projector)

    //then
    projector.getLastSnapshotView() should be(VersionedProjection(EventStoreVersion(0), ProjectionState(0, 0, 0)))
  }

  it should "ignore complete of the events stream" in {

    //given
    val projector = new InMemoryProjectionAtomic(new TestProjection())

    //when
    val errorStream = Observable.empty()
    errorStream.subscribe(projector)

    //then
    projector.getLastSnapshotView() should be(VersionedProjection(EventStoreVersion(0), ProjectionState(0, 0, 0)))
  }

}
