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

package eu.pmsoft.mcomponents.eventsourcing.eventstore.sql

import eu.pmsoft.mcomponents.eventsourcing.EventStoreVersion
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import org.scalatest.{ FlatSpec, Matchers }

import scala.collection.immutable.SortedSet

class OrderedProjectionVersionCacheTest extends FlatSpec with Matchers {

  it should "drop lower versions" in {
    //given
    implicit val versionOrdering = new EventStoreProjectionOrdering[TheTestDomainSpecification, TheTestState]()
    val maxSize: Int = 2
    val cache = OrderedProjectionVersionCache[TheTestDomainSpecification, TheTestState](maxSize, SortedSet.empty)
      def createVersion(version: Long): AtomicEventStoreStateSql[TheTestDomainSpecification, TheTestState] = {
        AtomicEventStoreStateSql[TheTestDomainSpecification, TheTestState](EventStoreVersion(version), TestStateInMemory())
      }
    //when
    val afterAllUpdates = (cache /: Range.inclusive(0, 3)) {
      case (updated, nextVersion) =>
        updated.pushState(createVersion(nextVersion.toLong))
    }
    //then
    afterAllUpdates.versions.size should be(maxSize)
    afterAllUpdates.versions should be(Set(createVersion(2L), createVersion(3L)))
  }
}
