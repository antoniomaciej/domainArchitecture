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

package eu.pmsoft.domain.minstance

import eu.pmsoft.domain.minstance.components._
import eu.pmsoft.domain.model.ComponentSpec
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.typelevel.scalatest.DisjunctionMatchers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test to try to find a dsl definition for components with dependencies.
 *
 * Components are:
 * FrontendComponent
 * BackendComponent
 * ProcessOneComponent
 * ProcessTwoComponent
 *
 *
 * Dependencies are (A <- B : B is injected in A)
 * FrontendComponent <- BackendComponent
 * BackendComponent <- (ProcessOneComponent,ProcessTwoComponent)
 */
class MicroComponentModelTest extends ComponentSpec {

  it should "create components and inject dependencies as futures" in {

    val registry = MicroComponentRegistry.create()

    //Create components independently
    val frontend = new FrontendComponent {
      override def backendRef: Future[BackendComponentApi] =
        registry.bindComponent(ApiContract(classOf[BackendComponentApi]))
    }
    val backend = new BackendComponent {
      override def processTwo: Future[ProcessTwoComponentApi] =
        registry.bindComponent(ApiContract(classOf[ProcessTwoComponentApi]))

      override def processOne: Future[ProcessOneComponentApi] =
        registry.bindComponent(ApiContract(classOf[ProcessOneComponentApi]))
    }
    val processOne = new ProcessOneComponent {}
    val processTwo = new ProcessTwoComponent {}

    //Register the components on the components registry
    registry.registerComponent(frontend)
    registry.registerComponent(processOne)
    registry.registerComponent(processTwo)
    registry.registerComponent(backend)

    // Bind all local and remote dependencies
    registry.initializeInstances().futureValue shouldBe \/-

    val frontendApi = registry.bindComponent(ApiContract(classOf[FrontendComponentApi])).futureValue

    frontendApi.callBackend().futureValue shouldBe List("ONE", "TWO")

  }
}
