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

package eu.pmsoft.domain.util.atom

import java.util.concurrent.atomic._

import scala.annotation.tailrec
import scalaz.{-\/, \/, \/-}

object Atomic {
  def apply[A](init: A): Atomic[A] = new Impl(new AtomicReference(init))

  private class Impl[A](state: AtomicReference[A]) extends Atomic[A] {
    def apply(): A = state.get()

    def updateAndGetWithCondition[E](f: A => A, c: A => E \/ A): E \/ A = transformWithConditionImpl(f, c)

    @tailrec private final def transformWithConditionImpl[E](fun: A => A, cond: A => E \/ A): E \/ A = {
      val v = state.get()
      cond(v) match {
        case e@ -\/(_) => e
        case \/-(valid) =>
          val newValue = fun(v)
          if (state.compareAndSet(v, newValue)) {
            \/-(newValue)
          } else {
            transformWithConditionImpl(fun, cond)
          }
      }
    }
  }

}

trait Atomic[A] {
  def apply(): A

  def updateAndGetWithCondition[E](f: A => A, c: A => E \/ A): E \/ A
}
