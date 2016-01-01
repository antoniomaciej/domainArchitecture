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

package eu.pmsoft.mcomponents.eventsourcing.eventstore

import eu.pmsoft.mcomponents.eventsourcing.{ EventStoreRange, EventStoreVersion }
import org.scalatest.{ FlatSpec, Matchers }

class EventStoreRangeUtils$Test extends FlatSpec with Matchers {
  val nrOfTestEvents = 20
  val testList = (1 to nrOfTestEvents).toList

  it should "extract open ranges correctly" in {

    EventStoreRangeUtils.extractRangeFromList(testList, EventStoreRange(EventStoreVersion.zero, None)) should be(testList)
    EventStoreRangeUtils.extractRangeFromList(testList, EventStoreRange(EventStoreVersion(1L), None)) should be(testList)

    (2 to (nrOfTestEvents + 2)).map{ from =>
      val extracted = EventStoreRangeUtils.extractRangeFromList(testList, EventStoreRange(EventStoreVersion(from), None))
      extracted should be(testList.drop(from-1))
    }
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

    val startFrom = 10L
    val takeMoreThatAvailable = 100L
    EventStoreRangeUtils.extractRangeFromList(
      testList,
      EventStoreRange(
        EventStoreVersion(startFrom),
        Some(EventStoreVersion(takeMoreThatAvailable))
      )
    ) should be(testList.drop((startFrom - 1 ).toInt))

    EventStoreRangeUtils.extractRangeFromList(
      testList,
      EventStoreRange(
        EventStoreVersion(0L),
        Some(EventStoreVersion(0L))
      )
    ) shouldBe empty
  }
}
