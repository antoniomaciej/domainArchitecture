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
