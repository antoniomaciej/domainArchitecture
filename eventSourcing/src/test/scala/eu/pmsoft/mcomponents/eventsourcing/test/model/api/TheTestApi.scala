package eu.pmsoft.mcomponents.eventsourcing.test.model.api

import eu.pmsoft.mcomponents.eventsourcing.{ DomainCommandApi, ApiModuleProvided }
import eu.pmsoft.mcomponents.eventsourcing.test.model._
import eu.pmsoft.mcomponents.minstance.ReqResDataModel._
import eu.pmsoft.mcomponents.minstance.RequestErrorDomain

import scala.concurrent.{ ExecutionContext, Future }
import scalaz._
import scalaz.std.scalaFuture._

object TheTestApi {
  implicit val requestErrorDomain = RequestErrorDomain("TheTestDomain")

}

trait TheTestApi {

  def cmdOne(): Future[RequestResult[CommandOneResult]]

  def cmdTwo(create: Boolean): Future[RequestResult[CommandTwoResult]]

}

case class CommandOneResult()
case class CommandTwoResult()

trait TheTestApiModule extends ApiModuleProvided[TheTestDomainSpecification] {

  lazy val theApi: TheTestApi = new TheApiDispatcher(cmdApi)

}

class TheApiDispatcher(val commandApi: DomainCommandApi[TheTestDomainSpecification])(implicit val executionContext: ExecutionContext)
    extends TheTestApi {
  import TheTestApi._
  override def cmdOne(): Future[RequestResult[CommandOneResult]] =
    (for {
      cmdResult <- EitherT(commandApi.commandHandler.execute(TestCommandOne()).map(_.asResponse))
    } yield CommandOneResult()).run

  override def cmdTwo(create: Boolean): Future[RequestResult[CommandTwoResult]] =
    (for {
      cmdResult <- EitherT(commandApi.commandHandler.execute(TestCommandTwo(create)).map(_.asResponse))
    } yield CommandTwoResult()).run
}