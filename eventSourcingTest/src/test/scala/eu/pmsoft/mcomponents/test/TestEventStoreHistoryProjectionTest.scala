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

package eu.pmsoft.mcomponents.test

import eu.pmsoft.mcomponents.eventsourcing.EventStoreVersion
import org.scalatest.{ FlatSpec, Matchers }

class TestEventStoreHistoryProjectionTest extends FlatSpec with Matchers {

  it should "be empty on creation" in {
    //given
    val projection = new TestEventStoreHistoryProjection[TestLogicDomainSpecification]()
    //when
    //then
    projection.events() should be(List())
    projection.version() should be(EventStoreVersion.zero)
    projection.lastSnapshotVersion() should be(projection.version())
  }

  it should "record the projected events" in {
    //given
    val projection = new TestEventStoreHistoryProjection[TestLogicDomainSpecification]()
      def projectTestEvent(nr: Long): Unit = {
        projection.projectEvent(EventWithData(nr), EventStoreVersion(nr))
        projection.lastSnapshotVersion() should be(projection.version())
      }
    val nrOfEventsToTest = 20
    //when
    (1 to nrOfEventsToTest).foreach(index => projectTestEvent(index.toLong))
    //then
    projection.events() should be((1 to nrOfEventsToTest).reverse.map(index => EventWithData(index.toLong)).toList)
    projection.version() should be(EventStoreVersion(nrOfEventsToTest))
    projection.lastSnapshotVersion() should be(projection.version())
  }
  it should "throw exception when event version do not match" in {
    //given
    val projection = new TestEventStoreHistoryProjection[TestLogicDomainSpecification]()
      def projectTestEvent(nr: Long): Unit = {
        projection.projectEvent(EventWithData(nr), EventStoreVersion(nr))
        projection.lastSnapshotVersion() should be(projection.version())
      }
    val nrOfEventsToTest = 20
    //and
    (1 to nrOfEventsToTest).foreach(index => projectTestEvent(index.toLong))
    //when a incorrect event store version is projected, then throw IllegalStateException
    intercept[IllegalStateException] {
      projectTestEvent(nrOfEventsToTest + 2)
    }
    intercept[IllegalStateException] {
      projectTestEvent(nrOfEventsToTest - 2)
    }
  }
}

