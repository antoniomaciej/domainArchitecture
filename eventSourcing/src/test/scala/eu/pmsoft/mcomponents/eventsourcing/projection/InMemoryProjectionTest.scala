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
    projection.getProjectionView(EventStoreVersion(102L)).futureValue should be(VersionedProjection(EventStoreVersion(102L), ProjectionState(1, 1, 100)))

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
