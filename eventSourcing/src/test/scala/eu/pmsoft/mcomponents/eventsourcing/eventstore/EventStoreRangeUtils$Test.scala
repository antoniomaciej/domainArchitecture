package eu.pmsoft.mcomponents.eventsourcing.eventstore

import eu.pmsoft.mcomponents.eventsourcing.{ EventStoreRange, EventStoreVersion }
import org.scalatest.{ FlatSpec, Matchers }

class EventStoreRangeUtils$Test extends FlatSpec with Matchers {

  val testList = (1 to 20).toList

  it should "extract open ranges correctly" in {
    EventStoreRangeUtils.extractRangeFromList(testList, EventStoreRange(EventStoreVersion.zero, None)) should be(testList)
    EventStoreRangeUtils.extractRangeFromList(testList, EventStoreRange(EventStoreVersion(1L), None)) should be(testList)
    EventStoreRangeUtils.extractRangeFromList(testList, EventStoreRange(EventStoreVersion(2L), None)) should be(testList.drop(1))
    EventStoreRangeUtils.extractRangeFromList(testList, EventStoreRange(EventStoreVersion(10L), None)) should be(testList.drop(9))
  }

  it should "extract close ranges correctly" in {
    EventStoreRangeUtils.extractRangeFromList(
      testList,
      EventStoreRange(
        EventStoreVersion.zero,
        Some(EventStoreVersion(4L))
      )
    ) should be(testList.take(4))

    EventStoreRangeUtils.extractRangeFromList(
      testList,
      EventStoreRange(
        EventStoreVersion(4L),
        Some(EventStoreVersion(4L))
      )
    ) should be(testList.drop(3).take(1))

    EventStoreRangeUtils.extractRangeFromList(
      testList,
      EventStoreRange(
        EventStoreVersion(10L),
        Some(EventStoreVersion(100L))
      )
    ) should be(testList.drop(9))

    EventStoreRangeUtils.extractRangeFromList(
      testList,
      EventStoreRange(
        EventStoreVersion(0L),
        Some(EventStoreVersion(0L))
      )
    ) shouldBe empty
  }

}
