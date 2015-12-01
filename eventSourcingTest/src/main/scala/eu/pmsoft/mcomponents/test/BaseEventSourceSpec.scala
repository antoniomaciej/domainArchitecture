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

package eu.pmsoft.mcomponents.test

import java.util.concurrent.Executor

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel.CommandResultConfirmed
import eu.pmsoft.mcomponents.eventsourcing._
import org.joda.time.DateTime
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.time.{ Millis, Seconds, Span }
import org.typelevel.scalatest.DisjunctionMatchers

import scala.concurrent.{ ExecutionContext, Future }

abstract class BaseEventSourceSpec extends BaseEventSourceComponentTestSpec

abstract class BaseEventSourceComponentTestSpec extends FlatSpec with Matchers
    with PropertyChecks with ScalaFutures with AppendedClues with ParallelTestExecution with DisjunctionMatchers {

  val longInterval = 150
  val longTimeout = 4
  implicit val defaultPatience =
    PatienceConfig(timeout = Span(longTimeout, Seconds), interval = Span(longInterval, Millis))

}

trait GeneratedCommandSpecification[D <: DomainSpecification] {
  self: BaseEventSourceSpec =>

  /** Default executor for event tests
   */
  private implicit lazy val synchronousExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable) = task.run()
  })

  implicit lazy val eventSourcingConfiguration = EventSourcingConfiguration(synchronousExecutionContext, bindingInfrastructure, Set(backendStrategy))

  def backendStrategy: EventStoreBackendStrategy[D]

  def bindingInfrastructure: BindingInfrastructure

  implicit def eventSourceExecutionContext: EventSourceExecutionContext

  def implementationModule(): DomainModule[D]

  def createEmptyDomainModel(): DomainCommandApi[D] = eventSourceExecutionContext.assemblyDomainApplication(implementationModule())

  private def genModule: Gen[DomainCommandApi[D]] = Gen.wrap(Gen.const(createEmptyDomainModel()))

  def serial[T, B](l: Iterable[T])(fn: T => Future[B]): Future[List[B]] = (Future(List[B]()) /: l) {
    (previousFuture, nextValue) =>
      for {
        previousResults <- previousFuture
        nextResult <- fn(nextValue)
      } yield previousResults :+ nextResult
  }

  def buildGenerator(state: AtomicEventStoreView[D#State]): CommandGenerator[D#Command]

  def validateState(state: D#State): Unit

  def postCommandValidation(state: D#State, command: D#Command): Unit

  it should "provide a eventStore singleton " in {
    val domainImplementation = implementationModule()
    domainImplementation.eventStore should be(domainImplementation.eventStore)
  }
  it should "provide a side effect singleton " in {
    val domainImplementation = implementationModule()
    domainImplementation.sideEffects should be(domainImplementation.sideEffects)
  }

  it should "serialize and unserialize all events " in {
    //given
    val domainImplementation: DomainModule[D] = implementationModule()
    val commandApi: DomainCommandApi[D] = eventSourceExecutionContext.assemblyDomainApplication(domainImplementation)
    val generator = buildGenerator(commandApi.atomicProjection)
    val warmUpCommands = generator.generateWarmUpCommands.sample.get
    var commandsHistory = warmUpCommands
    withClue(s"WarmUpCommands: $warmUpCommands \n") {
      val warmUpResult = serial(warmUpCommands)(commandApi.commandHandler.execute)
      whenReady(warmUpResult) { results =>
        val firstFailure = results.find(_.isLeft)
        firstFailure shouldBe empty withClue ": Failure on warm up commands"
        forAll(generator.generateSingleCommands) {
          command: D#Command =>
            commandsHistory = command :: commandsHistory
            withClue(s"CommandsHistory: $commandsHistory \n") {
              val resultsFuture = commandApi.commandHandler.execute(command)
              whenReady(resultsFuture) { result =>
                withClue(s"last command result:$result \n") {
                  result shouldBe \/-
                }
              }
            }
        }
      }
    }
    val events = domainImplementation.eventStore.loadEvents(EventStoreRange(EventStoreVersion.zero, None))
    events.foreach { event =>
      val eventData: EventData = domainImplementation.schema.eventToData(event)
      val recreatedEvent = domainImplementation.schema.mapToEvent(EventDataWithNr(0L, eventData.eventBytes, new DateTime()))
      recreatedEvent should be(event)
    }
  }

  it should "accept any list of valid commands " in {
    forAll(genModule) {
      commandApi: DomainCommandApi[D] =>

        val generator = buildGenerator(commandApi.atomicProjection)
        val warmUpCommands = generator.generateWarmUpCommands.sample.get
        var commandsHistory = warmUpCommands
        withClue(s"WarmUpCommands: $warmUpCommands \n") {
          val warmUpResult = serial(warmUpCommands)(commandApi.commandHandler.execute)
          whenReady(warmUpResult) { results =>
            val firstFailure = results.find(_.isLeft)
            firstFailure shouldBe empty withClue ": Failure on warm up commands"

            validateState(commandApi.atomicProjection.lastSnapshot())

            forAll(generator.generateSingleCommands) {
              command: D#Command =>
                commandsHistory = command :: commandsHistory
                withClue(s"CommandsHistory: $commandsHistory \n") {
                  val resultsFuture = commandApi.commandHandler.execute(command)
                  whenReady(resultsFuture) { result =>
                    withClue(s"last command result:$result \n") {

                      result shouldBe \/-
                      validateState(commandApi.atomicProjection.lastSnapshot())
                      postCommandValidation(commandApi.atomicProjection.lastSnapshot(), command)
                    }
                  }
                }
            }
          }
        }
    }
  }

  it should "Run duplicated events without exceptions and with valid state" in {
    forAll(genModule) {
      commandApi: DomainCommandApi[D] =>
        val generator = buildGenerator(commandApi.atomicProjection)
        val warmUpCommands = generator.generateWarmUpCommands.sample.get
        var commandsHistory = warmUpCommands
        val warmUpResult = serial(warmUpCommands)(commandApi.commandHandler.execute)
        whenReady(warmUpResult) { results =>
          val firstFailure = results.find(_.isLeft)
          firstFailure shouldBe empty withClue ": Failure on warm up commands"

          validateState(commandApi.atomicProjection.lastSnapshot())

          forAll(generator.generateSingleCommands) {
            command: D#Command =>
                def executeSingleCommand(): Unit = {
                  val resultsFuture = commandApi.commandHandler.execute(command)
                  whenReady(resultsFuture) { result =>
                    withClue(s"last command result:$results \n") {
                      // Result can be -\/, but the resulting state must be valid
                      validateState(commandApi.atomicProjection.lastSnapshot())
                      postCommandValidation(commandApi.atomicProjection.lastSnapshot(), command)
                    }
                  }
                }
              // Execute two times the same command
              commandsHistory = command :: commandsHistory
              withClue(s"CommandsHistory: $commandsHistory \n") {
                executeSingleCommand()
              }
              commandsHistory = command :: commandsHistory
              withClue(s"CommandsHistory: $commandsHistory \n") {
                executeSingleCommand()
              }
          }
        }
    }
  }

}

trait CommandGenerator[C] extends ScalaFutures {
  def generateSingleCommands: Gen[C]

  def generateWarmUpCommands: Gen[List[C]]

}
