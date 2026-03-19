package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.state.PartResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * Thrown by [FakeProcessExiter] to interrupt flow and capture the exit code.
 */
internal class FakeProcessExitException(val exitCode: Int) : RuntimeException("FakeProcessExit(code=$exitCode)")

internal class FakeProcessExiter : ProcessExiter {
    override fun exit(code: Int): Nothing {
        throw FakeProcessExitException(code)
    }
}

internal class FakeConsoleOutput : ConsoleOutput {
    val printedMessages = mutableListOf<String>()
    val greenMessages = mutableListOf<String>()

    override fun printlnRed(message: String) {
        printedMessages.add(message)
    }

    override fun printlnGreen(message: String) {
        greenMessages.add(message)
    }
}

internal class FakeAllSessionsKiller(
    private val orderTracker: OrderTracker? = null,
) : AllSessionsKiller {
    var killAllSessionsCalled = false
        private set

    override suspend fun killAllSessions() {
        killAllSessionsCalled = true
        orderTracker?.events?.add(EVENT_KILL_SESSIONS)
    }

    companion object {
        const val EVENT_KILL_SESSIONS = "kill_sessions"
    }
}

internal class SpyTicketFailureLearningUseCase(
    private val shouldThrow: Exception? = null,
    private val orderTracker: OrderTracker? = null,
) : TicketFailureLearningUseCase {
    var recordedPartResult: PartResult? = null
        private set

    override suspend fun recordFailureLearning(partResult: PartResult) {
        recordedPartResult = partResult
        orderTracker?.events?.add(EVENT_LEARNING)
        shouldThrow?.let { throw it }
    }

    companion object {
        const val EVENT_LEARNING = "learning"
    }
}

/**
 * Tracks the order of operations across test fakes.
 */
internal class OrderTracker {
    val events = mutableListOf<String>()
}

// ── Tests ───────────────────────────────────────────────────────────────────

class FailedToExecutePlanUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN a FailedWorkflow result") {
            describe("WHEN handleFailure is called") {
                it("THEN prints failure reason") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedWorkflow("plan step X failed"),
                    )
                    result.fakeConsole.printedMessages.first() shouldContain "Workflow failed: plan step X failed"
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
                it("THEN prints crash details") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.AgentCrashed("segfault in agent"),
                    )
                    result.fakeConsole.printedMessages.first() shouldContain "Agent crashed: segfault in agent"
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
                it("THEN prints convergence failure") {
                    val result = executeAndCapture(
                        outFactory = outFactory,
                        partResult = PartResult.FailedToConverge("exceeded max iterations"),
                    )
                    result.fakeConsole.printedMessages.first() shouldContain
                        "Failed to converge: exceeded max iterations"
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
                    val fakeConsole = FakeConsoleOutput()
                    val fakeKiller = FakeAllSessionsKiller()
                    val fakeExiter = FakeProcessExiter()
                    val throwingLearning = SpyTicketFailureLearningUseCase(
                        shouldThrow = RuntimeException("LLM unavailable"),
                    )
                    val useCase = FailedToExecutePlanUseCaseImpl(
                        outFactory = outFactory,
                        consoleOutput = fakeConsole,
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
                    val fakeConsole = FakeConsoleOutput()
                    val fakeKiller = FakeAllSessionsKiller()
                    val fakeExiter = FakeProcessExiter()
                    val throwingLearning = SpyTicketFailureLearningUseCase(
                        shouldThrow = RuntimeException("LLM unavailable"),
                    )
                    val useCase = FailedToExecutePlanUseCaseImpl(
                        outFactory = outFactory,
                        consoleOutput = fakeConsole,
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
                        consoleOutput = FakeConsoleOutput(),
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

        describe("GIVEN an order tracker wired into all fakes") {
            describe("WHEN handleFailure is called") {
                it("THEN steps execute in order: print -> kill -> learning -> exit") {
                    val orderTracker = OrderTracker()
                    val fakeConsole = object : ConsoleOutput {
                        override fun printlnRed(message: String) {
                            orderTracker.events.add(EVENT_PRINT)
                        }
                        override fun printlnGreen(message: String) {
                            orderTracker.events.add(EVENT_PRINT)
                        }
                    }
                    val fakeKiller = FakeAllSessionsKiller(orderTracker)
                    val spyLearning = SpyTicketFailureLearningUseCase(orderTracker = orderTracker)
                    val fakeExiter = object : ProcessExiter {
                        override fun exit(code: Int): Nothing {
                            orderTracker.events.add(EVENT_EXIT)
                            throw FakeProcessExitException(code)
                        }
                    }

                    val useCase = FailedToExecutePlanUseCaseImpl(
                        outFactory = outFactory,
                        consoleOutput = fakeConsole,
                        allSessionsKiller = fakeKiller,
                        ticketFailureLearningUseCase = spyLearning,
                        processExiter = fakeExiter,
                    )

                    shouldThrow<FakeProcessExitException> {
                        useCase.handleFailure(PartResult.FailedWorkflow("ordering test"))
                    }

                    orderTracker.events shouldBe listOf(
                        EVENT_PRINT,
                        FakeAllSessionsKiller.EVENT_KILL_SESSIONS,
                        SpyTicketFailureLearningUseCase.EVENT_LEARNING,
                        EVENT_EXIT,
                    )
                }
            }
        }
    },
) {
    companion object {
        private const val EVENT_PRINT = "print"
        private const val EVENT_EXIT = "exit"
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private data class HandleFailureResult(
    val fakeConsole: FakeConsoleOutput,
    val fakeKiller: FakeAllSessionsKiller,
    val spyLearning: SpyTicketFailureLearningUseCase,
    val caughtException: FakeProcessExitException,
)

/**
 * Executes [FailedToExecutePlanUseCaseImpl.handleFailure] with fresh fakes,
 * capturing the [FakeProcessExitException] and console output.
 */
private suspend fun executeAndCapture(
    outFactory: com.asgard.core.out.OutFactory,
    partResult: PartResult,
): HandleFailureResult {
    val fakeConsole = FakeConsoleOutput()
    val fakeKiller = FakeAllSessionsKiller()
    val fakeExiter = FakeProcessExiter()
    val spyLearning = SpyTicketFailureLearningUseCase()

    val useCase = FailedToExecutePlanUseCaseImpl(
        outFactory = outFactory,
        consoleOutput = fakeConsole,
        allSessionsKiller = fakeKiller,
        ticketFailureLearningUseCase = spyLearning,
        processExiter = fakeExiter,
    )

    val exception = shouldThrow<FakeProcessExitException> {
        useCase.handleFailure(partResult)
    }

    return HandleFailureResult(
        fakeConsole = fakeConsole,
        fakeKiller = fakeKiller,
        spyLearning = spyLearning,
        caughtException = exception,
    )
}
