package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RetryMaxStep(nested: Vector[Step], limit: Int) extends WrapperStep {

  require(limit > 0, "rety max limit must be a positive number")

  val title = s"RetryMax block with limit '$limit'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def retryMaxSteps(steps: Vector[Step], session: Session, limit: Int, accLogs: Vector[LogInstruction], retriesNumber: Long, depth: Int): (Long, StepsResult) = {
      engine.runSteps(steps, session, Vector.empty, depth) match {
        case s @ SuccessStepsResult(_, sLogs) ⇒
          (retriesNumber, s.copy(logs = accLogs ++ sLogs))
        case f @ FailureStepsResult(_, fSession, eLogs) ⇒
          if (limit > 0)
            // In case of success all logs are returned but they are not printed by default.
            retryMaxSteps(steps, session, limit - 1, accLogs ++ eLogs, retriesNumber + 1, depth)
          else
            // In case of failure only the logs of the last run are shown to avoid giant traces.
            (retriesNumber, f.copy(logs = eLogs))
      }
    }

    val (repeatRes, executionTime) = engine.withDuration {
      retryMaxSteps(nested, session, limit, Vector.empty, 0, depth + 1)
    }

    val (retries, report) = repeatRes

    report match {
      case s: SuccessStepsResult ⇒
        val fullLogs = successTitleLog(depth) +: report.logs :+ SuccessLogInstruction(s"RetryMax block with limit '$limit' succeeded after '$retries' retries", depth, Some(executionTime))
        SuccessStepsResult(report.session, fullLogs)
      case f: FailureStepsResult ⇒
        val fullLogs = failedTitleLog(depth) +: report.logs :+ FailureLogInstruction(s"RetryMax block with limit '$limit' failed", depth, Some(executionTime))
        val failedStep = FailedStep(f.failedStep.step, RetryMaxBlockReachedLimit)
        FailureStepsResult(failedStep, report.session, fullLogs)
    }
  }
}

