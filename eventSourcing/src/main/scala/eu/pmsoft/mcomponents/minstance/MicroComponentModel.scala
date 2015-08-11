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

import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic

import scala.concurrent._
import scalaz.{-\/, \/, \/-}


trait MicroComponent[T] {

  def providedContact: MicroComponentContract[T]

  def app: Future[T]
}


object MicroComponentModel {

  def contractFor[T](version: ApiVersion, apiClass: Class[T]): MicroComponentContract[T] =
    MicroComponentContract[T](version, ApiContract[T](apiClass))
}

case class MicroComponentContract[T](apiVersion: ApiVersion, contract: ApiContract[T])

case class InMemoryComponentRegistry(components: Set[MicroComponent[_]] = Set(), initialized: Boolean = false)

case class ApiVersion(mayor: Int, minor: Int, release: Int)

case class ApiContract[T](apiClass: Class[T]) extends AnyVal


class LocalMicroComponentRegistry(implicit val executionContext: ExecutionContext) extends MicroComponentRegistry {

  val initializedState = Promise[InMemoryComponentRegistry]()
  private val stateRef = Atomic(InMemoryComponentRegistry())

  override def registerComponent(component: MicroComponent[_]): MicroComponentRegistrationError \/ ComponentRegistered = {
    def addToRegistry(state: InMemoryComponentRegistry): InMemoryComponentRegistry = state.copy(components = state.components + component)
    def checkThatCanRegister(state: InMemoryComponentRegistry): MicroComponentRegistrationError \/ InMemoryComponentRegistry
    = {
      if (state.initialized) {
        -\/(RegisterAlreadyInitialized())
      } else {
        state.components.find(_.providedContact == component.providedContact) match {
          case Some(existing) => -\/(ComponentAlreadyRegistered())
          case None => \/-(state)
        }
      }
    }
    stateRef.updateAndGetWithCondition(addToRegistry, checkThatCanRegister)
      .map(state => ComponentRegistered())
  }

  override def initializeInstances(): Future[MicroComponentRegistrationError \/ MicroComponentRegistrationConfirmation] = {
    def checkThatNotInitialized(state: InMemoryComponentRegistry): MicroComponentRegistrationError \/ InMemoryComponentRegistry =
      if (state.initialized) {
        -\/(RegisterAlreadyInitialized())
      } else if (state.components.isEmpty) {
        -\/(RegisterIsEmpty())
      } else {
        \/-(state)
      }

    def init(state: InMemoryComponentRegistry): InMemoryComponentRegistry = state.copy(initialized = true)

    stateRef.updateAndGetWithCondition(init, checkThatNotInitialized) match {
      case e@ -\/(_) => Future.successful(e)
      case i@ \/-(initState) =>
        initializedState.success(initState)
        Future.successful(\/-(ComponentRegistered()))
    }
  }

  override def bindComponent[T](apiContract: ApiContract[T]): Future[T] =
    lookupComponent(apiContract).flatMap {
      case -\/(error) => Future.failed(new IllegalStateException(s"$error"))
      case \/-(instance) => Future.successful(instance)
    }

  override def lookupComponent[T](apiContract: ApiContract[T]): Future[MicroComponentRegistrationError \/ T] = {
    initializedState.future.flatMap(extractDependency(_, apiContract))
  }

  private def extractDependency[T](state: InMemoryComponentRegistry, apiContract: ApiContract[T]): Future[MicroComponentRegistrationError \/ T] = {
    state.components.find(_.providedContact.contract == apiContract) match {
      case Some(component) => component.app.map(instance => \/-(instance.asInstanceOf[T]))
      case None => Future.successful(-\/(ComponentNotFound(s"component for api $apiContract not found")))
    }
  }
}
