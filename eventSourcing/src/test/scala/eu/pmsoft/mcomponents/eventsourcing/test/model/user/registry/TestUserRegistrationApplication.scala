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

package eu.pmsoft.mcomponents.eventsourcing.test.model.user.registry

import eu.pmsoft.mcomponents.eventsourcing._


object TestUserRegistrationApplication {
  def createApplication(infrastructure: TheTestInfrastructure)
                       (implicit eventSourceExecutionContext: EventSourceExecutionContext): TestUserRegistrationApplication =
    new TestUserRegistrationApplication(infrastructure)
}

trait TheTestInfrastructure {
  def sideEffects: TestSideEffects

  def atomicProjection: VersionedEventStoreView[TheTestAggregate, TheTestState]

  def storeStorage: AsyncEventStore[TheTestEvent, TheTestAggregate]
}

final class TestUserRegistrationApplication(val infrastructure: TheTestInfrastructure)
                                           (implicit val eventSourceExecutionContext: EventSourceExecutionContext)
  extends AbstractApplicationModule[TheTestCommand,
    TheTestEvent,
    TheTestAggregate,
    TheTestState] {

  override lazy val logic: DomainLogic[TheTestCommand,
    TheTestEvent,
    TheTestAggregate,
    TheTestState] = new TestTestUserRegistrationHandlerLogic(infrastructure.sideEffects)

  override lazy val transactionScopeCalculator: CommandToTransactionScope[TheTestCommand,
    TheTestAggregate,
    TheTestState] = new TestUserRegistrationCommandToTransactionScope()

  override lazy val atomicProjection: VersionedEventStoreView[TheTestAggregate,
    TheTestState] = infrastructure.atomicProjection

  override lazy val storeStorage: AsyncEventStore[TheTestEvent,
    TheTestAggregate] = infrastructure.storeStorage
}


