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
 *
 */

package eu.pmsoft.mcomponents.model.security.password.reset

import eu.pmsoft.domain.model._
import org.scalatest.{FlatSpec, Matchers}
import org.typelevel.scalatest.DisjunctionMatchers

class PasswordResetModelTransactionExtractorTest extends FlatSpec with Matchers with DisjunctionMatchers {

  it should "mark a error if no userId is in the transaction scope" in {
    val extractors = new PasswordResetModelTransactionExtractor {}
    extractors.extractUserFromAggregated(Map()) shouldBe -\/
  }

  it should "mark a error if 2 userId are in the transaction scope" in {
    val extractors = new PasswordResetModelTransactionExtractor {}
    extractors.extractUserFromAggregated(Map(
      UserIdFlowAggregate(UserID(0L)) -> 0L,
      UserIdFlowAggregate(UserID(1L)) -> 0L
    )) shouldBe -\/
  }

}
