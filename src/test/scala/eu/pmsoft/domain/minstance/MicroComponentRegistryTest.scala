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

import eu.pmsoft.domain.minstance.components.{FrontendComponentApi, ProcessOneComponent, ProcessOneComponentApi}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.typelevel.scalatest.DisjunctionMatchers

import scala.concurrent.ExecutionContext.Implicits.global

class MicroComponentRegistryTest extends FlatSpec with Matchers
with ScalaFutures with AppendedClues with ParallelTestExecution with DisjunctionMatchers {

  it should "resolve components independently of the registration order" in {
    val registry = MicroComponentRegistry.create()
    val processOne = new ProcessOneComponent {}
    // lookup before registering
    val lookup = registry.lookupComponent(ApiContract(classOf[ProcessOneComponentApi]))

    registry.registerComponent(processOne) shouldBe \/-
    registry.initializeInstances().futureValue shouldBe \/-
    lookup.futureValue shouldBe \/-
  }

  it should "resolve components independently of the registration order with bind" in {
    val registry = MicroComponentRegistry.create()
    val processOne = new ProcessOneComponent {}
    // bind before registering
    val bind = registry.bindComponent(ApiContract(classOf[ProcessOneComponentApi]))

    registry.registerComponent(processOne) shouldBe \/-
    registry.initializeInstances().futureValue shouldBe \/-
    bind.futureValue shouldBe a[ProcessOneComponentApi]
  }

  it should "fail when initialized two times" in {
    val registry = MicroComponentRegistry.create()
    registry.initializeInstances().futureValue shouldBe \/-
    registry.initializeInstances().futureValue shouldBe -\/
  }

  it should "not allow to register after initialization" in {
    val registry = MicroComponentRegistry.create()
    val processOne = new ProcessOneComponent {}
    registry.initializeInstances().futureValue shouldBe \/-
    registry.registerComponent(processOne) shouldBe -\/
  }

  it should "not allow to register two times the same component" in {
    val registry = MicroComponentRegistry.create()
    val processOne = new ProcessOneComponent {}
    registry.registerComponent(processOne) shouldBe \/-
    registry.registerComponent(processOne) shouldBe -\/
  }

  it should "fail to find components not registered" in {
    val registry = MicroComponentRegistry.create()
    registry.initializeInstances().futureValue shouldBe \/-
    registry.lookupComponent(ApiContract(classOf[FrontendComponentApi])).futureValue shouldBe -\/
  }

  it should "fail to find components not registered on bind" in {
    val registry = MicroComponentRegistry.create()
    registry.initializeInstances().futureValue shouldBe \/-
    registry.bindComponent(ApiContract(classOf[FrontendComponentApi])).failed.futureValue shouldBe a[IllegalStateException]
  }

}
