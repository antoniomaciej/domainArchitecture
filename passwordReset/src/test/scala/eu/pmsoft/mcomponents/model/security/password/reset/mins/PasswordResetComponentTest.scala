package eu.pmsoft.mcomponents.model.security.password.reset.mins

import eu.pmsoft.domain.model._
import eu.pmsoft.mcomponents.minstance.{ApiContract, MicroComponentRegistry}
import eu.pmsoft.mcomponents.model.security.password.reset.PasswordResetApplication
import eu.pmsoft.mcomponents.model.security.password.reset.inmemory.PasswordResetInMemoryApplication

import scala.concurrent.ExecutionContext

class PasswordResetComponentTest extends ComponentSpec {

  //TODO add a projection to validate state changes

  import scala.concurrent.ExecutionContext.Implicits.global

  it should "start a reset password flow and cancel" in {
    val app: PasswordResetInMemoryApplication = new PasswordResetInMemoryApplication()

    val api = createComponent(app)
    val initCall = api.initializeFlow(
      InitializePasswordResetFlowRequest(UserID(0L), SessionToken("sessionToken"))
    ).futureValue
    initCall should be(\/-)

    val state = app.applicationContextProvider.contextStateAtomicProjection.lastSnapshot().futureValue
    val processOp = state.findFlowByUserID(UserID(0L))
    processOp should not be empty
    val process = processOp.get

    api.cancelFlow(CancelPasswordResetFlowRequest(process.passwordResetToken)).futureValue should be(\/-)

  }
  it should "start a reset password flow and confirm" in {
    val app: PasswordResetInMemoryApplication = new PasswordResetInMemoryApplication()

    val api = createComponent(app)
    val initCall = api.initializeFlow(
      InitializePasswordResetFlowRequest(UserID(0L), SessionToken("sessionToken"))
    ).futureValue
    initCall should be(\/-)

    val state = app.applicationContextProvider.contextStateAtomicProjection.lastSnapshot().futureValue
    val processOp = state.findFlowByUserID(UserID(0L))
    processOp should not be empty
    val process = processOp.get

    api.confirmFlow(ConfirmPasswordResetFlowRequest(
      SessionToken("sessionToken"),
      process.passwordResetToken,
      UserPassword("Changed"))).futureValue should be(\/-)

  }

  def createComponent(testApp: PasswordResetInMemoryApplication): PasswordResetApi = {
    val registry = MicroComponentRegistry.create()

    val roleAuth = new PasswordResetComponent {
      override lazy val application: PasswordResetApplication = testApp
      override implicit lazy val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    }

    registry.registerComponent(roleAuth)
    registry.initializeInstances().futureValue shouldBe \/-
    registry.bindComponent(
      ApiContract(classOf[PasswordResetApi])
    ).futureValue
  }
}
