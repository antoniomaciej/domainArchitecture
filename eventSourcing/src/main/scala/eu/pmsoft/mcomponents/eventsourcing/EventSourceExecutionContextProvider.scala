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

import eu.pmsoft.mcomponents.eventsourcing.eventstore.AsyncEventStore

import scala.concurrent.ExecutionContext
import scalaz.{ -\/, \/, \/- }

object EventSourceExecutionContextProvider {

  def create()(implicit configuration: EventSourcingConfiguration): EventSourceExecutionContext = new EventSourceExecutionContextImpl()
}

//Implementation
class EventSourceExecutionContextImpl()(implicit val configuration: EventSourcingConfiguration) extends EventSourceExecutionContext {

  override def assemblyDomainApplication[D <: DomainSpecification](domainImplementation: DomainModule[D]): DomainCommandApi[D] = new DomainApplication(domainImplementation, this)
}

trait ExecutionContextFromConfiguration {
  self: EventSourcingConfigurationContext =>

  final implicit lazy val executionContext: ExecutionContext = configuration.executionContext

}

private class DomainApplication[D <: DomainSpecification](
  val logic:                       DomainModule[D],
  val eventSourceExecutionContext: EventSourceExecutionContext
)
    extends DomainCommandApi[D] {

  private lazy val eventStore = logic.eventStore

  lazy val commandHandler: AsyncEventCommandHandler[D] = new DomainLogicAsyncEventCommandHandler[D](
    logic.logic,
    logic.sideEffects,
    eventStore
  )(eventSourceExecutionContext)

  lazy val atomicProjection: VersionedEventStoreView[D#Aggregate, D#State] = eventStore
}

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._

import scala.concurrent.{ Future, Promise }
import scalaz._
import scalaz.std.scalaFuture._

private final class DomainLogicAsyncEventCommandHandler[D <: DomainSpecification](
  val logic:       DomainLogic[D],
  val sideEffects: D#SideEffects,
  val eventStore:  AsyncEventStore[D] with VersionedEventStoreView[D#Aggregate, D#State]
)(implicit val eventSourceExecutionContext: EventSourceExecutionContext)
    extends AsyncEventCommandHandler[D] with EventSourcingConfigurationContext with ExecutionContextFromConfiguration {

  override implicit def configuration: EventSourcingConfiguration = eventSourceExecutionContext.configuration

  private def applyCommand(command: D#Command, transactionState: VersionedProjection[D#Aggregate, D#State]) =
    Future.successful(logic.executeCommand(command, transactionState.transactionScopeVersion)(transactionState.projection, sideEffects))

  private def calculateTransactionScopeVersion(transactionScope: Set[D#Aggregate]): Future[EventSourceCommandFailure \/ VersionedProjection[D#Aggregate, D#State]] =
    eventStore.projection(transactionScope).map(\/-(_))

  private def extractLastSnapshot(): Future[EventSourceCommandFailure \/ D#State] =
    eventStore.lastSnapshot().map(\/-(_))

  final def execute(command: D#Command): Future[CommandResultConfirmed] = {
      def singleTry(): Future[CommandResult] = {
        (for {
          lastSnapshot <- EitherT(extractLastSnapshot())
          transactionScope <- EitherT(Future.successful(logic.calculateTransactionScope(command, lastSnapshot)))
          versionedProjection <- EitherT(calculateTransactionScopeVersion(transactionScope))
          events <- EitherT(applyCommand(command, versionedProjection))
          confirmation <- EitherT(eventStore.persistEvents(events, versionedProjection.transactionScopeVersion))
        } yield confirmation).run
      }
    val p = Promise[CommandResultConfirmed]()

      def executionLoop() {
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
