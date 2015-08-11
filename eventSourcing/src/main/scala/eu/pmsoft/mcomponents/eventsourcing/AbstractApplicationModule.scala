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

package eu.pmsoft.mcomponents.eventsourcing

import eu.pmsoft.mcomponents.eventsourcing.EventSourceCommandEventModel._

import scala.concurrent.{Future, Promise}
import scalaz._
import scalaz.std.scalaFuture._

trait AbstractApplicationModule[C, E, A, S] extends AbstractApplicationContract[C, E, A, S] with EventSourceExecutionContextProvided {

  lazy val commandHandler: AsyncEventCommandHandler[C] = new DomainLogicAsyncEventCommandHandler[C, E, A, S](
    logic,
    transactionScopeCalculator,
    atomicProjection,
    storeStorage
  )(eventSourceExecutionContext)

}


final class DomainLogicAsyncEventCommandHandler[C, E, A, S](val logic: DomainLogic[C, E, A, S],
                                                            val transactionScopeCalculator: CommandToTransactionScope[C, A, S],
                                                            val atomicProjection: VersionedEventStoreView[A, S],
                                                            val storeStorage: AsyncEventStore[E, A])
                                                           (implicit val eventSourceExecutionContext: EventSourceExecutionContext)
  extends DomainLogicSpecification[C, E, A, S] with AsyncEventCommandHandler[C] with EventSourceExecutionContextProvided {

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
        confirmation <- EitherT(storeStorage.persistEvents(events, versionedProjection.transactionScopeVersion))
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
