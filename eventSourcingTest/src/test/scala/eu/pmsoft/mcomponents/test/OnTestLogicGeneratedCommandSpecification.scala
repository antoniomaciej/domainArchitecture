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

package eu.pmsoft.mcomponents.test

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreIdentification, EventStore }

import scala.concurrent.{ ExecutionContext, Future }
import scalaz._

/** Test to validate coverage
 */
class OnTestLogicGeneratedCommandSpecification extends BaseEventSourceSpec with GeneratedCommandSpecification[TestLogicDomainSpecification] {

  override def bindingInfrastructure: BindingInfrastructure = Mocked.shouldNotBeCalled

  override implicit def eventSourceExecutionContext: EventSourceExecutionContext = new EventSourceExecutionContext {

    override def assemblyDomainApplication[D <: DomainSpecification](domainImplementation: DomainModule[D]): DomainCommandApi[D] = new FakeDomainApi()

    override implicit def configuration: EventSourcingConfiguration = Mocked.shouldNotBeCalled
  }

  override def buildGenerator(state: AtomicEventStoreView[TheState]): CommandGenerator[TheCommand] = new TheCommandGenerator()

  override def postCommandValidation(state: TheState, command: TheCommand): Unit = state.ok

  override def validateState(state: TheState): Unit = state.ok

  override def implementationModule(): DomainModule[TestLogicDomainSpecification] = new DomainModule[TestLogicDomainSpecification] {
    override lazy val logic: DomainLogic[TestLogicDomainSpecification] = new TestDomainLogic()

    //TODO use mockito and create a generic nested mock/stub creator
    override lazy val eventStore: EventStore[TestLogicDomainSpecification] with VersionedEventStoreView[TheAggregate, TheState] =
      new EventStore[TestLogicDomainSpecification] with VersionedEventStoreView[TheAggregate, TheState] {
        override def identificationInfo: EventStoreIdentification[TestLogicDomainSpecification] = Mocked.shouldNotBeCalled

        override def projection(transactionScope: Set[TheAggregate]): Future[VersionedProjection[TheAggregate, TheState]] = Mocked.shouldNotBeCalled

        override def lastSnapshot(): Future[TheState] = Mocked.shouldNotBeCalled

        override def atLeastOn(storeVersion: EventStoreVersion): Future[TheState] = Mocked.shouldNotBeCalled

        override def persistEvents(events: List[TheEvent], transactionScopeVersion: Map[TheAggregate, Long]): Future[CommandResult] = Mocked.shouldNotBeCalled

        override def loadEvents(range: EventStoreRange): Future[Seq[TheEvent]] = Mocked.shouldNotBeCalled
      }

    override lazy val sideEffects: TheSideEffect = new TheSideEffect {}
  }
}

class FakeDomainApi[D <: DomainSpecification] extends DomainCommandApi[D] {
  lazy val fakeState: D#State = new TheState {
    override def ok: Boolean = true
  }.asInstanceOf[D#State]

  override def commandHandler: AsyncEventCommandHandler[D] = new AsyncEventCommandHandler[D] {
    override def execute(command: D#Command): Future[CommandResultConfirmed] =
      Future.successful(\/-(EventSourceCommandConfirmation(EventStoreVersion(0L))))
  }

  override def atomicProjection: VersionedEventStoreView[D#Aggregate, D#State] = new VersionedEventStoreView[D#Aggregate, D#State] {
    override def projection(transactionScope: Set[D#Aggregate]): Future[VersionedProjection[D#Aggregate, D#State]] =
      Future.successful(VersionedProjection[D#Aggregate, D#State](Map(), fakeState))

    override def lastSnapshot(): Future[D#State] = Future.successful(fakeState)

    override def atLeastOn(storeVersion: EventStoreVersion): Future[D#State] = Future.successful(fakeState)
  }
}
