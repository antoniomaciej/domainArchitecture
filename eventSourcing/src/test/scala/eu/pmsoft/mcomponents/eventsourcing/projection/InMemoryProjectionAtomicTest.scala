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
    projector.lastSnapshotView() should be(VersionedProjection(EventStoreVersion(0), ProjectionState(0, 0, 0)))
  }

  it should "ignore complete of the events stream" in {

    //given
    val projector = new InMemoryProjectionAtomic(new TestProjection())

    //when
    val errorStream = Observable.empty()
    errorStream.subscribe(projector)

    //then
    projector.lastSnapshotView() should be(VersionedProjection(EventStoreVersion(0), ProjectionState(0, 0, 0)))
  }
}
