package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.state.PartResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * Thrown by [FakeProcessExiter] to interrupt flow and capture the exit code.
 */
class FakeProcessExitException(val exitCode: Int) : RuntimeException("FakeProcessExit(code=$exitCode)")

class FakeProcessExiter : ProcessExiter {
    override fun exit(code: Int): Nothing {
        throw FakeProcessExitException(code)
    }
}

class FakeAllSessionsKiller : AllSessionsKiller {
    var killAllSessionsCalled = false
        private set

    override suspend fun killAllSessions() {
        killAllSessionsCalled = true
    }
}

class SpyTicketFailureLearningUseCase(
    private val shouldThrow: Exception? = null,
) : TicketFailureLearningUseCase {
    var recordedPartResult: PartResult? = null
        private set

    override suspend fun recordFailureLearning(partResult: PartResult) {
        recordedPartResult = partResult
        shouldThrow?.let { throw it }
    }
}

// ── Tests ───────────────────────────────────────────────────────────────────

class FailedToExecutePlanUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN a FailedWorkflow result") {
            describe("WHEN handleFailure is called") {
                it("THEN prints failure reason in red") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedWorkflow("plan step X failed"),
                    )
                    result.capturedOutput shouldContain "Workflow failed: plan step X failed"
                }

                it("THEN output contains ANSI red escape code") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedWorkflow("plan step X failed"),
                    )
                    result.capturedOutput shouldContain "\u001b[31m"
                }

                it("THEN output contains ANSI reset escape code") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedWorkflow("plan step X failed"),
                    )
                    result.capturedOutput shouldContain "\u001b[0m"
                }

                it("THEN kills all sessions") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedWorkflow("plan step X failed"),
                    )
                    result.fakeKiller.killAllSessionsCalled shouldBe true
                }

                it("THEN records failure learning") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedWorkflow("plan step X failed"),
                    )
                    result.spyLearning.recordedPartResult shouldBe PartResult.FailedWorkflow("plan step X failed")
                }

                it("THEN exits with code 1") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedWorkflow("plan step X failed"),
                    )
                    result.caughtException.exitCode shouldBe 1
                }
            }
        }

        describe("GIVEN an AgentCrashed result") {
            describe("WHEN handleFailure is called") {
                it("THEN prints crash details in red") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.AgentCrashed("segfault in agent"),
                    )
                    result.capturedOutput shouldContain "Agent crashed: segfault in agent"
                }

                it("THEN kills all sessions") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.AgentCrashed("segfault in agent"),
                    )
                    result.fakeKiller.killAllSessionsCalled shouldBe true
                }

                it("THEN exits with code 1") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.AgentCrashed("segfault in agent"),
                    )
                    result.caughtException.exitCode shouldBe 1
                }
            }
        }

        describe("GIVEN a FailedToConverge result") {
            describe("WHEN handleFailure is called") {
                it("THEN prints convergence failure in red") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedToConverge("exceeded max iterations"),
                    )
                    result.capturedOutput shouldContain "Failed to converge: exceeded max iterations"
                }

                it("THEN kills all sessions") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedToConverge("exceeded max iterations"),
                    )
                    result.fakeKiller.killAllSessionsCalled shouldBe true
                }

                it("THEN exits with code 1") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedToConverge("exceeded max iterations"),
                    )
                    result.caughtException.exitCode shouldBe 1
                }
            }
        }

        describe("GIVEN TicketFailureLearningUseCase throws") {
            describe("WHEN handleFailure is called") {
                it("THEN still exits with code 1 (learning failure is non-fatal)").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fakeKiller = FakeAllSessionsKiller()
                    val fakeExiter = FakeProcessExiter()
                    val throwingLearning = SpyTicketFailureLearningUseCase(
                        shouldThrow = RuntimeException("LLM unavailable"),
                    )
                    val useCase = FailedToExecutePlanUseCaseImpl(
                        outFactory = outFactory,
                        allSessionsKiller = fakeKiller,
                        ticketFailureLearningUseCase = throwingLearning,
                        processExiter = fakeExiter,
                    )
                    val exception = shouldThrow<FakeProcessExitException> {
                        useCase.handleFailure(PartResult.FailedWorkflow("some failure"))
                    }
                    exception.exitCode shouldBe 1
                }

                it("THEN still kills all sessions even when learning fails").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fakeKiller = FakeAllSessionsKiller()
                    val fakeExiter = FakeProcessExiter()
                    val throwingLearning = SpyTicketFailureLearningUseCase(
                        shouldThrow = RuntimeException("LLM unavailable"),
                    )
                    val useCase = FailedToExecutePlanUseCaseImpl(
                        outFactory = outFactory,
                        allSessionsKiller = fakeKiller,
                        ticketFailureLearningUseCase = throwingLearning,
                        processExiter = fakeExiter,
                    )
                    shouldThrow<FakeProcessExitException> {
                        useCase.handleFailure(PartResult.FailedWorkflow("some failure"))
                    }
                    fakeKiller.killAllSessionsCalled shouldBe true
                }
            }
        }

        describe("GIVEN a Completed result") {
            describe("WHEN handleFailure is called") {
                it("THEN throws IllegalArgumentException") {
                    val useCase = FailedToExecutePlanUseCaseImpl(
                        outFactory = outFactory,
                        allSessionsKiller = FakeAllSessionsKiller(),
                        ticketFailureLearningUseCase = SpyTicketFailureLearningUseCase(),
                        processExiter = FakeProcessExiter(),
                    )
                    val exception = shouldThrow<IllegalArgumentException> {
                        useCase.handleFailure(PartResult.Completed)
                    }
                    exception.message shouldContain "Completed"
                }
            }
        }
    },
)

// ── Helpers ─────────────────────────────────────────────────────────────────

private data class HandleFailureResult(
    val capturedOutput: String,
    val fakeKiller: FakeAllSessionsKiller,
    val spyLearning: SpyTicketFailureLearningUseCase,
    val caughtException: FakeProcessExitException,
)

/**
 * Executes [FailedToExecutePlanUseCaseImpl.handleFailure] with fresh fakes,
 * capturing stdout and the [FakeProcessExitException].
 */
private suspend fun executeAndCapture(
    outFactory: com.asgard.core.out.OutFactory,
    partResult: PartResult,
): HandleFailureResult {
    val fakeKiller = FakeAllSessionsKiller()
    val fakeExiter = FakeProcessExiter()
    val spyLearning = SpyTicketFailureLearningUseCase()

    val useCase = FailedToExecutePlanUseCaseImpl(
        outFactory = outFactory,
        allSessionsKiller = fakeKiller,
        ticketFailureLearningUseCase = spyLearning,
        processExiter = fakeExiter,
    )

    val originalOut = System.out
    val buffer = ByteArrayOutputStream()
    System.setOut(PrintStream(buffer))

    val exception: FakeProcessExitException
    try {
        exception = shouldThrow<FakeProcessExitException> {
            useCase.handleFailure(partResult)
        }
    } finally {
        System.setOut(originalOut)
    }

    return HandleFailureResult(
        capturedOutput = buffer.toString(),
        fakeKiller = fakeKiller,
        spyLearning = spyLearning,
        caughtException = exception,
    )
}
