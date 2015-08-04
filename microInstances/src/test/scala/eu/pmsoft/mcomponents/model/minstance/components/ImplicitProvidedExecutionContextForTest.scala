package eu.pmsoft.mcomponents.model.minstance.components

import scala.concurrent.ExecutionContext

trait ImplicitProvidedExecutionContextForTest {
  implicit def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
