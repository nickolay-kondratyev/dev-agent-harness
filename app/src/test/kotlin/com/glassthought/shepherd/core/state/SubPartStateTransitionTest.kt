package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class SubPartStateTransitionTest : AsgardDescribeSpec({

    // ── transitionTo: valid transitions from IN_PROGRESS ──

    describe("GIVEN status is IN_PROGRESS") {

        describe("WHEN signal is Done(COMPLETED)") {
            val transition = SubPartStatus.IN_PROGRESS.transitionTo(AgentSignal.Done(DoneResult.COMPLETED))

            it("THEN returns Complete") {
                transition shouldBe SubPartStateTransition.Complete
            }
        }

        describe("WHEN signal is Done(PASS)") {
            val transition = SubPartStatus.IN_PROGRESS.transitionTo(AgentSignal.Done(DoneResult.PASS))

            it("THEN returns Complete") {
                transition shouldBe SubPartStateTransition.Complete
            }
        }

        describe("WHEN signal is Done(NEEDS_ITERATION)") {
            val transition = SubPartStatus.IN_PROGRESS.transitionTo(AgentSignal.Done(DoneResult.NEEDS_ITERATION))

            it("THEN returns IterateContinue") {
                transition shouldBe SubPartStateTransition.IterateContinue
            }
        }

        describe("WHEN signal is FailWorkflow") {
            val transition = SubPartStatus.IN_PROGRESS.transitionTo(AgentSignal.FailWorkflow("some reason"))

            it("THEN returns Fail") {
                transition shouldBe SubPartStateTransition.Fail
            }
        }

        describe("WHEN signal is Crashed") {
            val transition = SubPartStatus.IN_PROGRESS.transitionTo(AgentSignal.Crashed("agent died"))

            it("THEN returns Fail") {
                transition shouldBe SubPartStateTransition.Fail
            }
        }

        describe("WHEN signal is SelfCompacted") {
            it("THEN throws IllegalStateException") {
                val exception = shouldThrow<IllegalStateException> {
                    SubPartStatus.IN_PROGRESS.transitionTo(AgentSignal.SelfCompacted)
                }
                exception.message shouldContain "SelfCompacted"
            }
        }
    }

    // ── transitionTo: invalid transitions from NOT_STARTED ──

    describe("GIVEN status is NOT_STARTED") {
        val allSignals = listOf(
            AgentSignal.Done(DoneResult.COMPLETED),
            AgentSignal.Done(DoneResult.PASS),
            AgentSignal.Done(DoneResult.NEEDS_ITERATION),
            AgentSignal.FailWorkflow("reason"),
            AgentSignal.Crashed("details"),
            AgentSignal.SelfCompacted,
        )

        allSignals.forEach { signal ->
            describe("WHEN signal is ${signal::class.simpleName}") {
                it("THEN throws IllegalStateException mentioning NOT_STARTED") {
                    val exception = shouldThrow<IllegalStateException> {
                        SubPartStatus.NOT_STARTED.transitionTo(signal)
                    }
                    exception.message shouldContain "NOT_STARTED"
                }
            }
        }
    }

    // ── transitionTo: terminal states ──

    describe("GIVEN status is COMPLETED (terminal)") {
        val allSignals = listOf(
            AgentSignal.Done(DoneResult.COMPLETED),
            AgentSignal.FailWorkflow("reason"),
            AgentSignal.Crashed("details"),
        )

        allSignals.forEach { signal ->
            describe("WHEN signal is ${signal::class.simpleName}") {
                it("THEN throws IllegalStateException mentioning terminal") {
                    val exception = shouldThrow<IllegalStateException> {
                        SubPartStatus.COMPLETED.transitionTo(signal)
                    }
                    exception.message shouldContain "COMPLETED is terminal"
                }
            }
        }
    }

    describe("GIVEN status is FAILED (terminal)") {
        val allSignals = listOf(
            AgentSignal.Done(DoneResult.COMPLETED),
            AgentSignal.FailWorkflow("reason"),
            AgentSignal.Crashed("details"),
        )

        allSignals.forEach { signal ->
            describe("WHEN signal is ${signal::class.simpleName}") {
                it("THEN throws IllegalStateException mentioning terminal") {
                    val exception = shouldThrow<IllegalStateException> {
                        SubPartStatus.FAILED.transitionTo(signal)
                    }
                    exception.message shouldContain "FAILED is terminal"
                }
            }
        }
    }

    // ── validateCanSpawn ──

    describe("GIVEN validateCanSpawn") {

        describe("WHEN status is NOT_STARTED") {
            val result = SubPartStatus.NOT_STARTED.validateCanSpawn()

            it("THEN returns Spawn") {
                result shouldBe SubPartStateTransition.Spawn
            }
        }

        listOf(SubPartStatus.IN_PROGRESS, SubPartStatus.COMPLETED, SubPartStatus.FAILED).forEach { status ->
            describe("WHEN status is $status") {
                it("THEN throws IllegalStateException") {
                    val exception = shouldThrow<IllegalStateException> {
                        status.validateCanSpawn()
                    }
                    exception.message shouldContain "spawn requires NOT_STARTED"
                    exception.message shouldContain status.name
                }
            }
        }
    }
})
