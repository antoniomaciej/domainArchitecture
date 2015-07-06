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

package eu.pmsoft.domain.minstance.components

import eu.pmsoft.domain.minstance._
import com.softwaremill.macwire._
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

trait BackendComponent extends MicroComponent[BackendComponentApi] {
  override def providedContact: MicroComponentContract[BackendComponentApi] =
    MicroComponentModel.contractFor(BackendComponentApi.version, classOf[BackendComponentApi])

  def processOne : Future[ProcessOneComponentApi]
  def processTwo : Future[ProcessTwoComponentApi]

  override lazy val app = for {
    one <- processOne
    two <- processTwo
  } yield wire[BackendComponentImplementation]
}

object BackendComponentApi {
  val version = ApiVersion(0, 0, 1)

}

trait BackendComponentApi {
  def handleCallToProcessOne(): Future[String]
  def handleCallToProcessTwo(): Future[String]
}

class BackendComponentImplementation(val one: Future[ProcessOneComponentApi], val two: Future[ProcessTwoComponentApi]) extends BackendComponentApi {
  override def handleCallToProcessOne(): Future[String] = one.flatMap( _.calculateOnOne())

  override def handleCallToProcessTwo(): Future[String] = two.flatMap( _.calculateOnTwo())
}
