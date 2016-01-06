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

package eu.pmsoft.mcomponents.minstance

import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.test.{ BaseEventSourceComponentTestSpec, BaseEventSourceSpec }

object TestServiceDomain {
  implicit val testDomain = RequestErrorDomain("testDomain")
}

import eu.pmsoft.mcomponents.minstance.ReqResDataModel._
import eu.pmsoft.mcomponents.minstance.TestServiceDomain._

class ReqResDataModelTest extends BaseEventSourceComponentTestSpec {

  it should "translate command results to responses - command failure case " in {
    //given
    val failure = scalaz.-\/(EventSourceCommandFailed(EventSourceCommandError(0L)))
    //when
    val responseFailure = failure.asResponse
    //then
    responseFailure should be(-\/)
    responseFailure.leftMap { f =>
      f.domain should be(testDomain)
      f.errorCode should be(RequestErrorCode(0L))
    }

  }

  it should "translate command results to responses - command success case " in {
    //given
    val success = scalaz.\/-(EventSourceCommandConfirmation[Int](EventStoreVersion(1L), 1))
    //when
    val responseFailure = success.asResponse
    //then
    responseFailure should be(\/-)
    responseFailure.map { s =>
      s.storeVersion should be(EventStoreVersion(1L))
    }

  }

  it should "translate command errors to response errors" in {
    //given
    val cmdError = EventSourceModelError("description", EventSourceCommandError(2L))
    //when
    val responseError = cmdError.toResponseError
    //then
    val expected = ResponseError(RequestErrorCode(2L), testDomain)
    responseError should equal(expected)

  }

}
