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

import com.softwaremill.macwire._
import eu.pmsoft.domain.minstance.{ApiVersion, MicroComponent, MicroComponentContract, MicroComponentModel}


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FrontendComponent extends MicroComponent[FrontendComponentApi] {
  override def providedContact: MicroComponentContract[FrontendComponentApi] =
    MicroComponentModel.contractFor(FrontendComponentApi.version, classOf[FrontendComponentApi])

  def backendRef : Future[BackendComponentApi]

  override lazy val app: Future[FrontendComponentApi] = for {
    backend <- backendRef
  } yield wire[FrontendComponentImplementation]
}

object FrontendComponentApi {

  val version = ApiVersion(0, 0, 1)
}

trait FrontendComponentApi {
  def callBackend(): Future[List[String]]
}

class FrontendComponentImplementation(val backend: Future[BackendComponentApi]) extends FrontendComponentApi {
  override def callBackend(): Future[List[String]] = for {
    service <- backend
    oneCall <- service.handleCallToProcessOne()
    twoCall <- service.handleCallToProcessTwo()
  } yield List(oneCall,twoCall)
}

