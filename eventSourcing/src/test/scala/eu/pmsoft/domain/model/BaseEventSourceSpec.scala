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

package eu.pmsoft.domain.model

import eu.pmsoft.mcomponents.eventsourcing.{AsyncEventCommandHandler, AtomicEventStoreProjection}
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.{AppendedClues, FlatSpec, Matchers, ParallelTestExecution}
import org.typelevel.scalatest.DisjunctionMatchers

import scala.concurrent.{ExecutionContext, Future}

abstract class BaseEventSourceSpec extends FlatSpec with Matchers
with PropertyChecks with ScalaFutures with AppendedClues with ParallelTestExecution with DisjunctionMatchers {

}

trait GeneratedCommandSpecification[C, E, S, M] {
  self: BaseEventSourceSpec =>

  def createEmptyModule(): M

  def asyncCommandHandler(contextModule: M): AsyncEventCommandHandler[C]

  def stateProjection(contextModule: M): AtomicEventStoreProjection[S]

  implicit def executionContext: ExecutionContext

  private def genModule: Gen[M] = Gen.wrap(Gen.const(createEmptyModule()))

  def serial[A, B](l: Iterable[A])(fn: A => Future[B]): Future[List[B]] = (Future(List[B]()) /: l) {
    (previousFuture, nextValue) => for {
      previousResults <- previousFuture
      nextResult <- fn(nextValue)
    } yield previousResults :+ nextResult
  }

  def buildGenerator(state: AtomicEventStoreProjection[S]): CommandGenerator[C]

  def validateState(state: S): Unit

  def postCommandValidation(state: S, command: C): Unit

  it should "accept any list of valid commands " in {
    forAll(genModule) {
      module: M =>

        val generator = buildGenerator(stateProjection(module))
        val warmUpCommands = generator.generateWarmUpCommands.sample.get
        var commandsHistory = warmUpCommands
        withClue(s"WarmUpCommands: $warmUpCommands \n") {
          val warmUpResult = serial(warmUpCommands)(asyncCommandHandler(module).execute)
          whenReady(warmUpResult) { results =>
            val firstFailure = results.find(_.isLeft)
            firstFailure shouldBe empty withClue ": Failure on warm up commands"

            validateState(stateProjection(module).lastSnapshot().futureValue)

            forAll(generator.generateSingleCommands) {
              command: C =>
                commandsHistory = command :: commandsHistory
                withClue(s"CommandsHistory: $commandsHistory \n") {

                  val resultsFuture = asyncCommandHandler(module).execute(command)

                  whenReady(resultsFuture) { results =>
                    withClue(s"last command result:$results \n") {

                      results shouldBe \/-
                      validateState(stateProjection(module).lastSnapshot().futureValue)
                      postCommandValidation(stateProjection(module).lastSnapshot().futureValue, command)
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
      module: M =>
        val generator = buildGenerator(stateProjection(module))
        val warmUpCommands = generator.generateWarmUpCommands.sample.get
        var commandsHistory = warmUpCommands
        val warmUpResult = serial(warmUpCommands)(asyncCommandHandler(module).execute)
        whenReady(warmUpResult) { results =>
          val firstFailure = results.find(_.isLeft)
          firstFailure shouldBe empty withClue ": Failure on warm up commands"

          validateState(stateProjection(module).lastSnapshot().futureValue)

          forAll(generator.generateSingleCommands) {
            command: C =>
              def executeSingleCommand(): Unit = {
                val resultsFuture = asyncCommandHandler(module).execute(command)
                whenReady(resultsFuture) { results =>
                  withClue(s"last command result:$results \n") {
                    validateState(stateProjection(module).lastSnapshot().futureValue)
                    postCommandValidation(stateProjection(module).lastSnapshot().futureValue, command)
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
