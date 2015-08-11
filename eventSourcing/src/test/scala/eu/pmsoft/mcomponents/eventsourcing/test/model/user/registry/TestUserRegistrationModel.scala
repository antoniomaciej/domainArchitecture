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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry

import eu.pmsoft.mcomponents.eventsourcing._

object TestUserRegistrationModel {

  val invalidErrorCode = 4001L
  val invalidErrorTest = EventSourceModelError("error message",
    EventSourceCommandError(invalidErrorCode))

}

//Model entities

//Aggregate
sealed trait TheTestAggregate

case class TestAggregateOne() extends TheTestAggregate

case class TestAggregateTwo() extends TheTestAggregate

//UserRegistrationModel commands

sealed trait TheTestCommand

case class TestCommandOne() extends TheTestCommand

case class TestCommandTwo(createTwo: Boolean) extends TheTestCommand


//UserRegistrationModel events

sealed trait TheTestEvent

case class TestEventOne() extends TheTestEvent

case class TestEventTwo() extends TheTestEvent

case class TestEventThree() extends TheTestEvent

