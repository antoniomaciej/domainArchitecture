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

package eu.pmsoft.mcomponents.model.security.password.reset

import java.util.concurrent.Executor

import eu.pmsoft.mcomponents.eventsourcing.{AsyncEventCommandHandler, AtomicEventStoreProjection}
import eu.pmsoft.mcomponents.model.security.password.reset.inmemory.PasswordResetInMemoryApplication

import scala.concurrent.ExecutionContext

class PasswordResetInMemoryApplicationTest extends PasswordResetModuleTest[PasswordResetInMemoryApplication] {

  //TODO: extract to one common place
  val synchronousExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable) = task.run()
  })

  override implicit def executionContext: ExecutionContext = synchronousExecutionContext


  override def createEmptyModule(): PasswordResetInMemoryApplication = new PasswordResetInMemoryApplication()

  override def asyncCommandHandler(contextModule: PasswordResetInMemoryApplication):
  AsyncEventCommandHandler[PasswordResetModelCommand] = contextModule.commandHandler

  override def stateProjection(contextModule: PasswordResetInMemoryApplication):
  AtomicEventStoreProjection[PasswordResetModelState] = contextModule.applicationContextProvider.contextStateAtomicProjection

}