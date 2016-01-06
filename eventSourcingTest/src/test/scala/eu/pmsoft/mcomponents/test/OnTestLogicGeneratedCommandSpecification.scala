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
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreRead, EventStoreID, EventStoreReference, EventStore }
import eu.pmsoft.mcomponents.eventsourcing.projection.VersionedProjection
import rx.Observable

import scala.concurrent.Future
import scala.reflect._
import scalaz._

/** Test to validate coverage
 */
class OnTestLogicGeneratedCommandSpecification extends BaseEventSourceSpec with GeneratedCommandSpecification[TestLogicDomainSpecification] {

  it should "provide a eventStoreConfiguration" in {
    eventSourcingConfiguration.backendStrategies should be(Set(EventStoreInMemory[TestLogicDomainSpecification](eventStoreReference)))
    eventSourcingConfiguration.bindingInfrastructure should be(bindingInfrastructure)
  }

  override lazy val bindingInfrastructure: BindingInfrastructure = new BindingInfrastructure {
    override def consumerApi: EventConsumerInfrastructure = Mocked.shouldNotBeCalled

    override def producerApi: EventProductionInfrastructure = Mocked.shouldNotBeCalled
  }

  val eventStoreReference = EventStoreReference[TestLogicDomainSpecification](
    EventStoreID("any"),
    classTag[TheEvent],
    classTag[TheAggregate]
  )

  override def backendStrategy: EventStoreBackendStrategy[TestLogicDomainSpecification] = EventStoreInMemory[TestLogicDomainSpecification](eventStoreReference)

  override implicit def eventSourceExecutionContext: EventSourceExecutionContext = new EventSourceExecutionContext {

    override def assemblyDomainApplication[D <: DomainSpecification](domainImplementation: DomainModule[D]): DomainCommandApi[D] = new FakeDomainApi()

    override implicit def eventSourcingConfiguration: EventSourcingConfiguration = Mocked.shouldNotBeCalled
  }

  override def buildGenerator(state: AtomicEventStoreView[TheState])(implicit eventStoreRead: EventStoreRead[TestLogicDomainSpecification]): CommandGenerator[TheCommand] = new TheCommandGenerator()

  override def postCommandValidation(state: TheState, command: TheCommand,
                                     result: EventSourceCommandConfirmation[TheAggregate])(implicit eventStoreRead: EventStoreRead[TestLogicDomainSpecification]): Unit = state.ok

  override def validateState(state: TheState)(implicit eventStoreRead: EventStoreRead[TestLogicDomainSpecification]): Unit = state.ok

  override def implementationModule(): DomainModule[TestLogicDomainSpecification] = new DomainModule[TestLogicDomainSpecification] {
    override lazy val logic: DomainLogic[TestLogicDomainSpecification] = new TestDomainLogic()

    //TODO use mockito and create a generic nested mock/stub creator
    override lazy val eventStore: EventStore[TestLogicDomainSpecification] with VersionedEventStoreView[TheAggregate, TheState] =
      new EventStore[TestLogicDomainSpecification] with VersionedEventStoreView[TheAggregate, TheState] {

        override def loadEvents(range: EventStoreRange): Seq[TheEvent] = Seq(EventOne(), EventTwo())

        override def calculateAtomicTransactionScopeVersion(logic: DomainLogic[TestLogicDomainSpecification], command: TheCommand): Future[CommandToAtomicState[TestLogicDomainSpecification]] = Mocked.shouldNotBeCalled

        override def persistEvents(
          events:        List[TheEvent],
          aggregateRoot: TheAggregate, AtomicTransactionScope: AtomicTransactionScope[TestLogicDomainSpecification]
        ): Future[CommandResult[TestLogicDomainSpecification]] = Mocked.shouldNotBeCalled

        override def lastSnapshot(): TheState = Mocked.shouldNotBeCalled

        override def eventStoreVersionUpdates(): Observable[EventStoreVersion] = Mocked.shouldNotBeCalled

        override def atLeastOn(storeVersion: EventStoreVersion): Future[VersionedProjection[TheState]] = Mocked.shouldNotBeCalled

        override def loadEventsForAggregate(aggregate: TheAggregate): Seq[TheEvent] = Mocked.shouldNotBeCalled
      }

    override lazy val sideEffects: TheSideEffect = new TheSideEffect {}

    override def schema: EventSerializationSchema[TestLogicDomainSpecification] = new EventSerializationSchema[TestLogicDomainSpecification] {
      override def mapToEvent(data: EventDataWithNr): TheEvent = {
        val number = data.eventBytes(0).toInt
        number match {
          case 1 => EventOne()
          case 2 => EventTwo()
          case _ => throw new IllegalStateException("serialization must produce only 1 or 2 values")
        }
      }

      override def buildReference(aggregate: TheAggregate): AggregateReference = aggregate match {
        case AggregateOne() => AggregateReference(1, "one")
        case AggregateTwo() => AggregateReference(1, "two")
      }

      override def eventToData(event: TheEvent): EventData = event match {
        case EventOne()        => EventData(Array(1.toByte))
        case EventTwo()        => EventData(Array(2.toByte))
        case EventWithData(nr) => EventData(Array(nr.toByte))
      }
    }
  }
}

class FakeDomainApi[D <: DomainSpecification] extends DomainCommandApi[D] {
  lazy val fakeState: D#State = new TheState {
    override def ok: Boolean = true
  }.asInstanceOf[D#State]
  lazy val fakeAggregate: D#Aggregate = AggregateOne().asInstanceOf[D#Aggregate]

  override def commandHandler: AsyncEventCommandHandler[D] = new AsyncEventCommandHandler[D] {
    override def execute(command: D#Command): Future[CommandResultConfirmed[D#Aggregate]] =
      Future.successful(\/-(EventSourceCommandConfirmation(EventStoreVersion.zero, fakeAggregate)))
  }

  override def atomicProjection: VersionedEventStoreView[D#Aggregate, D#State] = new VersionedEventStoreView[D#Aggregate, D#State] {

    override def lastSnapshot(): D#State = fakeState

    override def atLeastOn(storeVersion: EventStoreVersion): Future[VersionedProjection[D#State]] = Future.successful(VersionedProjection(EventStoreVersion.zero, fakeState))
  }

  override def eventSourcingConfiguration: EventSourcingConfiguration = Mocked.shouldNotBeCalled
}
