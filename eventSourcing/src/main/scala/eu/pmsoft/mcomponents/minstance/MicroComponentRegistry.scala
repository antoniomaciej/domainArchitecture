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
package eu.pmsoft.mcomponents.minstance

import com.typesafe.scalalogging.LazyLogging
import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic

import scala.concurrent.{ Promise, ExecutionContext, Future }
import scalaz._

object MicroComponentRegistry {

  def create()(implicit executionContext: ExecutionContext): MicroComponentRegistry = new LocalMicroComponentRegistry()

}

trait MicroComponentRegistry {

  def registerComponent(component: MicroComponent[_]): MicroComponentRegistrationError \/ MicroComponentRegistrationConfirmation

  def bindComponent[T](apiContract: ApiContract[T]): Future[T]

  def lookupComponent[T](apiContract: ApiContract[T]): Future[MicroComponentRegistrationError \/ T]

  /** Initialize all instance registered.
   *  @return the number of registered instances
   */
  def initializeInstances(): Future[MicroComponentRegistrationError \/ MicroComponentRegistrationConfirmation]
}

//Internal implementation
private class LocalMicroComponentRegistry(implicit val executionContext: ExecutionContext) extends MicroComponentRegistry with LazyLogging {

  val initializedState = Promise[InMemoryComponentRegistry]()
  private val stateRef = Atomic(InMemoryComponentRegistry())

  override def registerComponent(component: MicroComponent[_]): MicroComponentRegistrationError \/ ComponentRegistered = {
      def addToRegistry(state: InMemoryComponentRegistry): InMemoryComponentRegistry = state.copy(components = state.components + component)
      def checkThatCanRegister(state: InMemoryComponentRegistry): MicroComponentRegistrationError \/ InMemoryComponentRegistry = {
        if (state.initialized) {
          -\/(RegisterAlreadyInitialized())
        }
        else {
          state.components.find(_.providedContact == component.providedContact) match {
            case Some(existing) => -\/(ComponentAlreadyRegistered())
            case None           => \/-(state)
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
        }
        else if (state.components.isEmpty) {
          -\/(RegisterIsEmpty())
        }
        else {
          \/-(state)
        }

      def init(state: InMemoryComponentRegistry): InMemoryComponentRegistry = state.copy(initialized = true)

    stateRef.updateAndGetWithCondition(init, checkThatNotInitialized) match {
      case e @ -\/(_) => Future.successful(e)
      case i @ \/-(initState) =>
        logger.info(s"initializingState $initState")
        initializedState.success(initState)
        Future.successful(\/-(ComponentRegistered()))
    }
  }

  override def bindComponent[T](apiContract: ApiContract[T]): Future[T] =
    lookupComponent(apiContract).flatMap {
      case -\/(error)    => Future.failed(new IllegalStateException(s"$error"))
      case \/-(instance) => Future.successful(instance)
    }

  override def lookupComponent[T](apiContract: ApiContract[T]): Future[MicroComponentRegistrationError \/ T] = {
    initializedState.future.flatMap(extractDependency(_, apiContract))
  }

  private def extractDependency[T](state: InMemoryComponentRegistry, apiContract: ApiContract[T]): Future[MicroComponentRegistrationError \/ T] = {
    state.components.find(_.providedContact.contract == apiContract) match {
      case Some(component) => component.app.map(instance => \/-(instance.asInstanceOf[T]))
      case None            => Future.successful(-\/(ComponentNotFound(s"component for api $apiContract not found")))
    }
  }
}

private case class InMemoryComponentRegistry(components: Set[MicroComponent[_]] = Set(), initialized: Boolean = false)
