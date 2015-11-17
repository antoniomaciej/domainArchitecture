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

package eu.pmsoft.mcomponents.eventsourcing.atomic

import org.scalatest.{ FlatSpec, Matchers }
import org.typelevel.scalatest.DisjunctionMatchers

import scalaz.\/

class AtomicTest extends FlatSpec with Matchers with DisjunctionMatchers {

  //Atomic reference contract is not tested, just the wrapping api

  it should "set get initial value" in {
    val expected = new Object()
    Atomic(expected)() should be(expected)
  }

  def checkCondition(state: AtomicTestState): String \/ AtomicTestState =
    if (state.passCondition) {
      scalaz.\/-(state)
    }
    else {
      scalaz.-\/("condition not passed")
    }

  def updateState(markToSet: String)(state: AtomicTestState): AtomicTestState =
    state.copy(marked = markToSet)

  it should "not update if condition is not passed" in {
    //given
    val atomic = Atomic(AtomicTestState(passCondition = false))
    //when condition is not passed by the state
    val afterUpdateTry = atomic.updateAndGetWithCondition(updateState("will not pass"), checkCondition)
    //then the internal atomic state is not changed
    atomic().marked should be("")
    //and the error is returned
    afterUpdateTry should be(-\/)
  }
  it should "update if condition is passed" in {
    //given
    val atomic = Atomic(AtomicTestState(passCondition = true))
    //when condition is not passed by the state
    val afterUpdateTry = atomic.updateAndGetWithCondition(updateState("will pass"), checkCondition)
    //then the internal atomic state is not changed
    atomic().marked should be("will pass")
    //and the error is returned
    afterUpdateTry should be(\/-)
  }
  it should "updateAndGet do not check any condition" in {
    //given
    val atomic = Atomic(AtomicTestState(passCondition = false))
    //when condition is not passed by the state
    val stateAfterUpdate = atomic.updateAndGet(updateState("will not check"))
    //then the internal atomic state is not changed
    atomic().marked should be("will not check")
    //and the error is returned
    stateAfterUpdate.marked should be("will not check")
  }

}

case class AtomicTestState(passCondition: Boolean, marked: String = "")
