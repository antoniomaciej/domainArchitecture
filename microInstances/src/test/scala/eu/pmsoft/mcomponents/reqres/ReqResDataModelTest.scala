package eu.pmsoft.mcomponents.reqres

import eu.pmsoft.domain.model.ComponentSpec
import eu.pmsoft.mcomponents.eventsourcing._

object TestServiceDomain {
  implicit val testDomain = RequestErrorDomain("testDomain")
}

class ReqResDataModelTest extends ComponentSpec {

  it should "translate command results to responses - command failure case " in {
    import ReqResDataModel._
    import TestServiceDomain._
    //given
    val failure = scalaz.-\/(EventSourceCommandFailed(EventSourceCommandError(0L)))
    //when
    val responseFailure = failure.asResponse
    //then
    responseFailure should be(-\/)
    responseFailure.leftMap { f =>
      f.domain should be(testDomain)
      f.errorCode should be(RequestErrorCode(0L))
    }

  }

  it should "translate command results to responses - command success case " in {
    import ReqResDataModel._
    import TestServiceDomain._
    //given
    val failure = scalaz.\/-(EventSourceCommandConfirmation(EventStoreVersion(1L)))
    //when
    val responseFailure = failure.asResponse
    //then
    responseFailure should be(\/-)
    responseFailure.map { s =>
      s.storeVersion should be(EventStoreVersion(1L))
    }

  }

  it should "translate command errors to response errors" in {
    import ReqResDataModel._
    import TestServiceDomain._
    //given
    val cmdError = EventSourceModelError("description", EventSourceCommandError(2L))
    //when
    val responseError = cmdError.toResponseError
    //then
    val expected = ResponseError(RequestErrorCode(2L), testDomain)
    responseError should equal(expected)

  }

}
