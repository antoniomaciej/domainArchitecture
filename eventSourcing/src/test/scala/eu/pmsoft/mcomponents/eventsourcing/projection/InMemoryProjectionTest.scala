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

package eu.pmsoft.mcomponents.eventsourcing.projection

import eu.pmsoft.mcomponents.eventsourcing.inmemory.LocalBindingInfrastructure
import eu.pmsoft.mcomponents.eventsourcing._
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import eu.pmsoft.mcomponents.eventsourcing.test.model.api.TheTestApiModule
import eu.pmsoft.mcomponents.test.BaseEventSourceComponentTestSpec
import rx.Observable
import rx.observers.TestSubscriber

class InMemoryProjectionTest extends BaseEventSourceComponentTestSpec {

  it should "Create stream of events" in {
    //given
    val config: EventSourcingConfiguration = configuration()
    val module: TheTestApiModule = apiModule(config)
    val theApi = module.theApi
    //and a projection is bind
    val testSubscriber = new TestSubscriber[VersionedEvent[TheTestDomainSpecification]]()
    val events: Observable[VersionedEvent[TheTestDomainSpecification]] = config.bindingInfrastructure.consumerApi.eventStoreStream(TheTestDomainModule.eventStoreReference, EventStoreVersion.zero)
    events.subscribe(testSubscriber)
    //when
    theApi.cmdOne().futureValue shouldBe \/-
    theApi.cmdTwo(true).futureValue shouldBe \/-
    theApi.cmdTwo(false).futureValue shouldBe \/-
    //then

    import scala.collection.JavaConversions._
    testSubscriber.assertNoErrors()
    testSubscriber.assertNoTerminalEvent()
    testSubscriber.getOnNextEvents.toList should be(List(
      VersionedEvent[TheTestDomainSpecification](EventStoreVersion(1L), TestEventOne()),
      VersionedEvent[TheTestDomainSpecification](EventStoreVersion(2L), TestEventTwo()),
      VersionedEvent[TheTestDomainSpecification](EventStoreVersion(3L), TestEventThree())
    ))
  }

  it should "Create inMemory projections" in {
    //given
    val config: EventSourcingConfiguration = configuration()
    val projection: EventSourceProjectionView[ProjectionState] = InMemoryProjections.bindProjection(TheTestDomainModule.eventStoreReference, new TestProjection(), config.bindingInfrastructure)
    val module: TheTestApiModule = apiModule(config)
    val theApi = module.theApi
    //and a projection is bind
    //when
    theApi.cmdOne().futureValue shouldBe \/-
    theApi.cmdTwo(true).futureValue shouldBe \/-
    (1 to 100).foreach { index =>
      theApi.cmdTwo(false).futureValue shouldBe \/-
    }
    //then
    val expectedEventStoreVersion = 102L
    val expectedNrOfThreeEvents = 100
    projection.getProjectionView(EventStoreVersion(expectedEventStoreVersion)).futureValue should be(VersionedProjection(EventStoreVersion(expectedEventStoreVersion), ProjectionState(1, 1, expectedNrOfThreeEvents)))

  }

  def configuration(): EventSourcingConfiguration = {
    EventSourcingConfiguration(
      scala.concurrent.ExecutionContext.Implicits.global,
      LocalBindingInfrastructure.create(),
      Set(
        EventStoreInMemory(TheTestDomainModule.eventStoreReference)
      )
    )
  }

  def apiModule(implicit eventSourcingConfiguration: EventSourcingConfiguration): TheTestApiModule = {
    val eventSourceExecutionContext = EventSourceExecutionContextProvider.create()
    val domainApi: DomainCommandApi[TheTestDomainSpecification] = eventSourceExecutionContext.assemblyDomainApplication(new TheTestDomainModule())

    new TheTestApiModule {
      override def cmdApi: DomainCommandApi[TheTestDomainSpecification] = domainApi
    }

  }
}

class TestProjection extends EventSourceProjectionCreationLogic[TheTestDomainSpecification, ProjectionState] {
  override def zero(): ProjectionState = ProjectionState()

  override def projectEvent(state: ProjectionState, version: EventStoreVersion, event: TheTestEvent): ProjectionState =
    event match {
      case TestEventOne()                      => state.copy(one = state.one + 1)
      case TestEventTwo()                      => state.copy(two = state.two + 1)
      case TestEventThree()                    => state.copy(three = state.three + 1)
      case e @ TestEventThread(threadNr, data) => state
    }
}

case class ProjectionState(one: Int = 0, two: Int = 0, three: Int = 0)
