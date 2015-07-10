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

import eu.pmsoft.domain.model.EventSourceDataModel._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scalaz._
import scalaz.std.scalaFuture._


object EventSourceEngine {

}


trait DomainLogic[C, E, A, S] {
  def executeCommand(command: C, transactionScope: Map[A, Long])(implicit state: S): CommandToEventsResult[E]
}

trait EventSourceProjection[E] {

  def projectEvent(event: E, storeVersion: EventStoreVersion): Unit

  def lastSnapshotVersion(): EventStoreVersion
}

trait AtomicEventStoreProjection[+P] extends OrderedEventStoreProjector[P] {

  def lastSnapshot(): Future[P]
}

trait VersionedEventStoreProjection[A, +P] extends AtomicEventStoreProjection[P] {

  def projection(transactionScope: Set[A]): Future[VersionedProjection[A, P]]
}

case class VersionedProjection[A, +P](transactionScopeVersion: Map[A, Long], projection: P)

trait OrderedEventStoreProjector[+P] {
  def atLeastOn(storeVersion: EventStoreVersion): Future[P]
}

trait CommandToTransactionScope[C, A, S] {
  def calculateTransactionScope(command: C, state: S): CommandToAggregateResult[A]
}

//Async domain handler
trait AsyncEventStore[E, A] {

  def persistEvents(events: List[E], transactionScopeVersion: Map[A, Long]): Future[CommandResult]

}

trait AsyncEventHandlingModule[C, E, S] {

  implicit def executionContext: ExecutionContext

  def commandHandler: AsyncEventCommandHandler[C]

  def state: AtomicEventStoreProjection[S]

}

trait AsyncEventCommandHandler[C] {

  def execute(command: C): Future[CommandResultConfirmed]

}

trait DomainLogicAsyncEventCommandHandler[C, E, A, S] extends AsyncEventCommandHandler[C] {

  protected def logic: DomainLogic[C, E, A, S]

  protected def store: AsyncEventStore[E, A]

  protected def atomicProjection: VersionedEventStoreProjection[A, S]

  protected def transactionScopeCalculator: CommandToTransactionScope[C, A, S]

  implicit def executionContext: ExecutionContext

  private def applyCommand(command: C, transactionState: VersionedProjection[A, S]) =
    Future.successful(logic.executeCommand(command, transactionState.transactionScopeVersion)(transactionState.projection))

  private def calculateTransactionScopeVersion(transactionScope: Set[A]):
  Future[EventSourceCommandFailure \/ VersionedProjection[A, S]] =
    atomicProjection.projection(transactionScope).map(\/-(_))

  private def extractLastSnapshot():
  Future[EventSourceCommandFailure \/ S] =
    atomicProjection.lastSnapshot().map(\/-(_))

  final def execute(command: C): Future[CommandResultConfirmed] = {
    def singleTry(): Future[CommandResult] = {
      (for {
        lastSnapshot <- EitherT(extractLastSnapshot())
        transactionScope <- EitherT(Future.successful(transactionScopeCalculator.calculateTransactionScope(command, lastSnapshot)))
        versionedProjection <- EitherT(calculateTransactionScopeVersion(transactionScope))
        events <- EitherT(applyCommand(command, versionedProjection))
        confirmation <- EitherT(store.persistEvents(events, versionedProjection.transactionScopeVersion))
      } yield confirmation).run
    }
    val p = Promise[CommandResultConfirmed]()

    def executionLoop() {
      singleTry() onComplete {
        case util.Failure(exception) => p.failure(exception)
        case util.Success(value) => value match {
          case r@ \/-(b) => p.complete(util.Success(r))
          case -\/(a) => a match {
            case EventSourceCommandFailed(error) => p.complete(util.Success(-\/(EventSourceCommandFailed(error))))
            case EventSourceCommandRollback() => executionLoop()
          }
        }
      }
    }
    executionLoop()
    p.future
  }
}
