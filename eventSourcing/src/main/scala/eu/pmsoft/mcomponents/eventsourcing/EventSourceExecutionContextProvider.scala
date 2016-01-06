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

package eu.pmsoft.mcomponents.eventsourcing

import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStore

import scala.concurrent.ExecutionContext
import scalaz.{ -\/, \/, \/- }

object EventSourceExecutionContextProvider {

  def create()(implicit configuration: EventSourcingConfiguration): EventSourceExecutionContext = new EventSourceExecutionContextImpl()
}

abstract class ApiModuleProvided[D <: DomainSpecification] extends ApiModule[D] {
  override implicit lazy val executionContext: ExecutionContext = cmdApi.eventSourcingConfiguration.executionContext
}

//Implementation
class EventSourceExecutionContextImpl()(implicit val eventSourcingConfiguration: EventSourcingConfiguration) extends EventSourceExecutionContext {

  override def assemblyDomainApplication[D <: DomainSpecification](domainImplementation: DomainModule[D]): DomainCommandApi[D] =
    new DomainApplication(domainImplementation, eventSourcingConfiguration)
}

//TODO XXX1 change this to implicit conversion
trait ExecutionContextFromConfiguration {
  self: EventSourcingConfigurationContext =>

  final implicit lazy val executionContext: ExecutionContext = eventSourcingConfiguration.executionContext

}

private class DomainApplication[D <: DomainSpecification](
    val logic:                      DomainModule[D],
    val eventSourcingConfiguration: EventSourcingConfiguration
) extends DomainCommandApi[D] {

  private lazy val eventStore = logic.eventStore

  lazy val commandHandler: AsyncEventCommandHandler[D] = new DomainLogicAsyncEventCommandHandler[D](
    logic.logic,
    logic.sideEffects,
    eventStore,
    eventSourcingConfiguration
  )

  lazy val atomicProjection: VersionedEventStoreView[D#Aggregate, D#State] = eventStore
}

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._

import scala.concurrent.{ Future, Promise }
import scalaz._
import scalaz.std.scalaFuture._

private final class DomainLogicAsyncEventCommandHandler[D <: DomainSpecification](
    val logic:                      DomainLogic[D],
    val sideEffects:                D#SideEffects,
    val eventStore:                 EventStore[D] with VersionedEventStoreView[D#Aggregate, D#State],
    val eventSourcingConfiguration: EventSourcingConfiguration
) extends AsyncEventCommandHandler[D] with EventSourcingConfigurationContext with ExecutionContextFromConfiguration {

  private def applyCommand(command: D#Command, atomicTransactionScope: AtomicTransactionScope[D]): Future[CommandToEventsResult[D]] =
    Future.successful(logic.executeCommand(command, atomicTransactionScope.transactionScopeVersion)(atomicTransactionScope.projectionView, sideEffects))

  private def calculateAtomicTransactionScope(logic: DomainLogic[D], command: D#Command): Future[CommandToAtomicState[D]] =
    eventStore.calculateAtomicTransactionScopeVersion(logic, command)

  final def execute(command: D#Command): Future[CommandResultConfirmed[D#Aggregate]] = {
      def singleTry(): Future[CommandResult[D]] = {
        (for {
          atomicTransactionScope <- EitherT(calculateAtomicTransactionScope(logic, command))
          eventsAndRoot <- EitherT(applyCommand(command, atomicTransactionScope))
          confirmation <- EitherT(eventStore.persistEvents(eventsAndRoot.events, eventsAndRoot.rootAggregate, atomicTransactionScope))
        } yield confirmation).run
      }
    val p = Promise[CommandResultConfirmed[D#Aggregate]]()

      def executionLoop(): Unit = {
        singleTry() onComplete {
          case util.Failure(exception) => p.failure(exception)
          case util.Success(value) => value match {
            case r @ \/-(b) => p.complete(util.Success(r))
            case -\/(a) => a match {
              case EventSourceCommandFailed(error) => p.complete(util.Success(-\/(EventSourceCommandFailed(error))))
              case EventSourceCommandRollback()    => executionLoop()
            }
          }
        }
      }
    executionLoop()
    p.future
  }
}
