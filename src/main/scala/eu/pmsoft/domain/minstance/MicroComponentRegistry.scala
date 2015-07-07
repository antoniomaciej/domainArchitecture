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

import eu.pmsoft.domain.util.atom.Atomic

import scala.concurrent.{ExecutionContext, Future, Promise}
import scalaz.{-\/, \/, \/-}

object MicroComponentRegistry {

  def create()(implicit executionContext: ExecutionContext): MicroComponentRegistry = new LocalMicroComponentRegistry()

}

sealed trait RegistrationError

case class RegisterAlreadyInitialized() extends RegistrationError

case class ComponentAlreadyRegistered() extends RegistrationError

case class ComponentNotFound(msg: String) extends RegistrationError

trait MicroComponentRegistry {

  def registerComponent(component: MicroComponent[_]): RegistrationError \/ InMemoryComponentRegistry

  def bindComponent[T](apiContract: ApiContract[T]): Future[T]

  def lookupComponent[T](apiContract: ApiContract[T]): Future[RegistrationError \/ T]

  /**
   * Initialize all instance registered.
   * @return the number of registered instances
   */
  def initializeInstances(): Future[RegistrationError \/ InMemoryComponentRegistry]
}

class LocalMicroComponentRegistry(implicit val executionContext: ExecutionContext) extends MicroComponentRegistry {

  private val stateRef = Atomic(InMemoryComponentRegistry())

  override def registerComponent(component: MicroComponent[_]): RegistrationError \/ InMemoryComponentRegistry = {
    def addToRegistry(state: InMemoryComponentRegistry): InMemoryComponentRegistry = state.copy(components = state.components + component)
    def checkThatCanRegister(state: InMemoryComponentRegistry): RegistrationError \/ InMemoryComponentRegistry = {
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
  }

  override def initializeInstances(): Future[RegistrationError \/ InMemoryComponentRegistry] = {
    def checkThatNotInitialized(state: InMemoryComponentRegistry): RegistrationError \/ InMemoryComponentRegistry =
      if (state.initialized) {
        -\/(RegisterAlreadyInitialized())
      } else {
        \/-(state)
      }

    def init(state: InMemoryComponentRegistry): InMemoryComponentRegistry = state.copy(initialized = true)

    stateRef.updateAndGetWithCondition(init, checkThatNotInitialized) match {
      case e@ -\/(_) => Future.successful(e)
      case i@ \/-(initState) =>
        initializedState.success(initState)
        Future.successful(i)
    }
  }


  val initializedState = Promise[InMemoryComponentRegistry]()


  override def bindComponent[T](apiContract: ApiContract[T]): Future[T] =
    lookupComponent(apiContract).flatMap {
      case -\/(error) => Future.failed(new IllegalStateException(s"$error"))
      case \/-(instance) => Future.successful(instance)
    }

  override def lookupComponent[T](apiContract: ApiContract[T]): Future[RegistrationError \/ T] = {
    initializedState.future.flatMap(extractDependency(_, apiContract))
  }

  private def extractDependency[T](state: InMemoryComponentRegistry, apiContract: ApiContract[T]): Future[RegistrationError \/ T] = {
    state.components.find(_.providedContact.contract == apiContract) match {
      case Some(component) => component.app.map(instance => \/-(instance.asInstanceOf[T]))
      case None => Future.successful(-\/(ComponentNotFound(s"component for api $apiContract not found")))
    }
  }
}

case class InMemoryComponentRegistry(components: Set[MicroComponent[_]] = Set(), initialized: Boolean = false)
