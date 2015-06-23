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

package eu.pmsoft.domain.inmemory.user.registry

import java.util.concurrent.Executor

import eu.pmsoft.domain.model.user.registry.{UserRegistrationCommand, UserRegistrationModuleTest, UserRegistrationState}
import eu.pmsoft.domain.model.{AsyncEventCommandHandler, AtomicEventStoreProjection}

import scala.concurrent.ExecutionContext

class UserRegistrationInMemoryApplicationTest extends UserRegistrationModuleTest[UserRegistrationInMemoryApplication] {

  val synchronousExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable) = task.run()
  })

  implicit def executionContext: ExecutionContext = synchronousExecutionContext

  override def createEmptyModule(): UserRegistrationInMemoryApplication = new UserRegistrationInMemoryApplication()

  override def asyncCommandHandler(contextModule: UserRegistrationInMemoryApplication)
  : AsyncEventCommandHandler[UserRegistrationCommand] = contextModule.commandHandler

  override def stateProjection(contextModule: UserRegistrationInMemoryApplication)
  : AtomicEventStoreProjection[UserRegistrationState] = contextModule.applicationContextProvider.contextStateAtomicProjection
}
