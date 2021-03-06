package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext

case class LogDurationStep(nested: Vector[Step], label: String) extends WrapperStep {

  val title = s"Log duration block with label '$label' started"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {
    val titleLog = DebugLogInstruction(title, depth)
    val (repeatRes, executionTime) = engine.withDuration {
      engine.runSteps(nested, session, Vector.empty, depth + 1)
    }
    val fullLogs = titleLog +: repeatRes.logs :+ DebugLogInstruction(s"Log duration block with label '$label' ended", depth, Some(executionTime))
    repeatRes match {
      case s: SuccessStepsResult ⇒ s.copy(logs = fullLogs)
      case f: FailureStepsResult ⇒ f.copy(logs = fullLogs)
    }
  }

}
