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

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

object MicroComponentRegistry {

  def create()(implicit executionContext: ExecutionContext): MicroComponentRegistry = new LocalMicroComponentRegistry()

}

sealed trait RegistrationError

case class RegisterIsEmpty() extends RegistrationError

case class RegisterAlreadyInitialized() extends RegistrationError

case class ComponentAlreadyRegistered() extends RegistrationError

case class ComponentNotFound(msg: String) extends RegistrationError

sealed trait RegistrationConfirmation

case class ComponentRegistered() extends RegistrationConfirmation

trait MicroComponentRegistry {

  def registerComponent(component: MicroComponent[_]): RegistrationError \/ RegistrationConfirmation

  def bindComponent[T](apiContract: ApiContract[T]): Future[T]

  def lookupComponent[T](apiContract: ApiContract[T]): Future[RegistrationError \/ T]

  /**
   * Initialize all instance registered.
   * @return the number of registered instances
   */
  def initializeInstances(): Future[RegistrationError \/ RegistrationConfirmation]
}

