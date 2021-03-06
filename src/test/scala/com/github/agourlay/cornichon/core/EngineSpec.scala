package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.steps.regular.AssertStep
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class EngineSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "An engine" when {
    "runScenario" must {
      "execute all steps of a scenario" in {
        val session = Session.newSession
        val steps = Vector(AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2 + 1, 3)))
        val s = Scenario("test", steps)
        engine.runScenario(session)(s).isSuccess should be(true)
      }

      "stop at first failed step" in {
        val session = Session.newSession
        val step1 = AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2, 2))
        val step2 = AssertStep[Int]("second step", s ⇒ SimpleStepAssertion(4, 5))
        val step3 = AssertStep[Int]("third step", s ⇒ SimpleStepAssertion(1, 1))
        val steps = Vector(
          step1, step2, step3
        )
        val s = Scenario("test", steps)
        val res = engine.runScenario(session)(s)
        withClue(s"logs were ${res.logs}") {
          res match {
            case s: SuccessScenarioReport ⇒ fail("Should be a FailedScenarioReport")
            case f: FailureScenarioReport ⇒
              f.failedSteps.head.error.msg should be("""
              |expected result was:
              |'4'
              |but actual result is:
              |'5'""".stripMargin.trim)
          }
        }
      }

      "accumulated errors if 'main' and 'finally' fail" in {
        val session = Session.newSession
        val mainStep = AssertStep[Boolean]("main step", s ⇒ SimpleStepAssertion(true, false))
        val finallyStep = AssertStep[Boolean]("finally step", s ⇒ SimpleStepAssertion(true, false))
        val s = Scenario("test", Vector(mainStep))
        val res = engine.runScenario(session, Vector(finallyStep))(s)
        res match {
          case s: SuccessScenarioReport ⇒ fail("Should be a FailedScenarioReport")
          case f: FailureScenarioReport ⇒
            f.msg should be("""Scenario 'test' failed at step(s):
             |
             |main step
             |with error:
             |expected result was:
             |'true'
             |but actual result is:
             |'false'
             |
             |and
             |
             |finally step
             |with error:
             |expected result was:
             |'true'
             |but actual result is:
             |'false'
             |
             |
             |""".stripMargin)
        }
      }
    }
  }
}
