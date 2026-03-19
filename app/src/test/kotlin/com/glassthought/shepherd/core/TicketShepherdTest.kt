package com.glassthought.shepherd.core

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.executor.PartExecutor
import com.glassthought.shepherd.core.executor.PartExecutorFactory
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.interrupt.InterruptHandler
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.usecase.finalcommit.FinalCommitUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase
import com.glassthought.shepherd.usecase.planning.SetupPlanUseCase
import com.glassthought.shepherd.usecase.ticketstatus.TicketStatusUpdater
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path

// ── Test Fakes (prefixed with "Ts" to avoid redeclaration with same-package test files) ─────

/**
 * Thrown by [TsProcessExiter] to interrupt flow and capture the exit code.
 */
private class TsProcessExitException(val exitCode: Int) : RuntimeException("TsProcessExit(code=$exitCode)")

private class TsProcessExiter : ProcessExiter {
    override fun exit(code: Int): Nothing {
        throw TsProcessExitException(code)
    }
}

private class TsConsoleOutput : ConsoleOutput {
    val redMessages = mutableListOf<String>()
    val greenMessages = mutableListOf<String>()

    override fun printlnRed(message: String) {
        redMessages.add(message)
    }

    override fun printlnGreen(message: String) {
        greenMessages.add(message)
    }
}

private class TsInterruptHandler : InterruptHandler {
    var installCalled = false
        private set

    override fun install() {
        installCalled = true
    }
}

private class TsAllSessionsKiller : AllSessionsKiller {
    var killAllSessionsCalled = false
        private set

    override suspend fun killAllSessions() {
        killAllSessionsCalled = true
    }
}

private class TsSetupPlanUseCase(
    private val partsToReturn: List<Part>,
) : SetupPlanUseCase {
    override suspend fun setup(): List<Part> = partsToReturn
}

/**
 * Captures the [PartResult] passed to handleFailure and throws [TsHandleFailureException]
 * to simulate the "never returns" behavior.
 */
private class TsHandleFailureException(val partResult: PartResult) :
    RuntimeException("TsHandleFailure(result=$partResult)")

private class TsFailedToExecutePlanUseCase : FailedToExecutePlanUseCase {
    override suspend fun handleFailure(failedResult: PartResult): Nothing {
        throw TsHandleFailureException(failedResult)
    }
}

private class TsFinalCommitUseCase : FinalCommitUseCase {
    var commitIfDirtyCalled = false
        private set

    override suspend fun commitIfDirty() {
        commitIfDirtyCalled = true
    }
}

private class TsTicketStatusUpdater : TicketStatusUpdater {
    var markDoneCalled = false
        private set

    override suspend fun markDone() {
        markDoneCalled = true
    }
}

/**
 * A [PartExecutor] that returns a pre-configured [PartResult].
 */
private class TsPartExecutor(private val result: PartResult) : PartExecutor {
    override suspend fun execute(): PartResult = result
}

/**
 * A [PartExecutorFactory] that returns the next pre-configured [TsPartExecutor] from a queue.
 * Records which parts were passed to [create].
 */
private class TsPartExecutorFactory(
    private val results: MutableList<PartResult>,
) : PartExecutorFactory {

    val createdForParts = mutableListOf<Part>()

    override fun create(part: Part): PartExecutor {
        createdForParts.add(part)
        val result = results.removeFirst()
        return TsPartExecutor(result)
    }
}

// ── Test Helpers ────────────────────────────────────────────────────────────

private val testAiOutputStructure = AiOutputStructure(
    repoRoot = Path.of("/tmp/test-repo"),
    branch = "test-branch",
)

private fun createTestPart(name: String): Part =
    Part(
        name = name,
        phase = Phase.EXECUTION,
        description = "test part $name",
        subParts = listOf(SubPart(name = "impl", role = "DOER", agentType = "ClaudeCode", model = "sonnet")),
    )

private data class TsFixture(
    val shepherd: TicketShepherd,
    val fakeConsole: TsConsoleOutput,
    val fakeInterruptHandler: TsInterruptHandler,
    val fakeKiller: TsAllSessionsKiller,
    val fakeFailedUseCase: TsFailedToExecutePlanUseCase,
    val fakeFinalCommit: TsFinalCommitUseCase,
    val fakeStatusUpdater: TsTicketStatusUpdater,
    val fakeFactory: TsPartExecutorFactory,
    val currentState: CurrentState,
)

private fun createFixture(
    parts: List<Part> = emptyList(),
    results: List<PartResult> = emptyList(),
    outFactory: com.asgard.core.out.OutFactory,
): TsFixture {
    val fakeConsole = TsConsoleOutput()
    val fakeInterruptHandler = TsInterruptHandler()
    val fakeKiller = TsAllSessionsKiller()
    val fakeFailedUseCase = TsFailedToExecutePlanUseCase()
    val fakeFinalCommit = TsFinalCommitUseCase()
    val fakeStatusUpdater = TsTicketStatusUpdater()
    val fakeFactory = TsPartExecutorFactory(results.toMutableList())
    val currentState = CurrentState(parts = mutableListOf())

    val deps = TicketShepherdDeps(
        setupPlanUseCase = TsSetupPlanUseCase(parts),
        failedToExecutePlanUseCase = fakeFailedUseCase,
        interruptHandler = fakeInterruptHandler,
        allSessionsKiller = fakeKiller,
        partExecutorFactory = fakeFactory,
        consoleOutput = fakeConsole,
        processExiter = TsProcessExiter(),
        finalCommitUseCase = fakeFinalCommit,
        ticketStatusUpdater = fakeStatusUpdater,
        aiOutputStructure = testAiOutputStructure,
        out = outFactory.getOutForClass(TicketShepherd::class),
        ticketId = "test-ticket-42",
    )

    val shepherd = TicketShepherd(
        deps = deps,
        currentState = currentState,
        originatingBranch = "main",
        tryNumber = 1,
    )

    return TsFixture(
        shepherd = shepherd,
        fakeConsole = fakeConsole,
        fakeInterruptHandler = fakeInterruptHandler,
        fakeKiller = fakeKiller,
        fakeFailedUseCase = fakeFailedUseCase,
        fakeFinalCommit = fakeFinalCommit,
        fakeStatusUpdater = fakeStatusUpdater,
        fakeFactory = fakeFactory,
        currentState = currentState,
    )
}

// ── Tests ───────────────────────────────────────────────────────────────────

class TicketShepherdTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN a plan with two parts that both complete successfully") {
            val parts = listOf(createTestPart("part-1"), createTestPart("part-2"))
            val results = listOf(PartResult.Completed, PartResult.Completed)

            describe("WHEN run() is called") {

                it("THEN installs the interrupt handler") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeInterruptHandler.installCalled shouldBe true
                }

                it("THEN performs the final commit") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeFinalCommit.commitIfDirtyCalled shouldBe true
                }

                it("THEN marks the ticket as done") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeStatusUpdater.markDoneCalled shouldBe true
                }

                it("THEN kills all sessions as defensive cleanup") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeKiller.killAllSessionsCalled shouldBe true
                }

                it("THEN prints success message in green with ticket ID") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    val expectedMessage = "Workflow completed successfully for ticket test-ticket-42."
                    fixture.fakeConsole.greenMessages.first() shouldBe expectedMessage
                }

                it("THEN exits with code 0") {
                    val fixture = createFixture(parts, results, outFactory)
                    val exception = shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    exception.exitCode shouldBe 0
                }

                it("THEN appends parts to currentState") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.currentState.parts.size shouldBe 2
                }

                it("THEN creates executors for each part via the factory") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeFactory.createdForParts.map { it.name } shouldBe listOf("part-1", "part-2")
                }

                it("THEN activeExecutor is null after run completes") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.shepherd.activeExecutor shouldBe null
                }
            }
        }

        describe("GIVEN a plan where the first part returns FailedWorkflow") {
            val parts = listOf(createTestPart("part-1"), createTestPart("part-2"))
            val results = listOf(PartResult.FailedWorkflow("step X broke"))

            describe("WHEN run() is called") {

                it("THEN delegates to FailedToExecutePlanUseCase with the failure result") {
                    val fixture = createFixture(parts, results, outFactory)
                    val exception = shouldThrow<TsHandleFailureException> { fixture.shepherd.run() }
                    exception.partResult shouldBe PartResult.FailedWorkflow("step X broke")
                }

                it("THEN does NOT call finalCommitUseCase") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsHandleFailureException> { fixture.shepherd.run() }
                    fixture.fakeFinalCommit.commitIfDirtyCalled shouldBe false
                }

                it("THEN does NOT mark the ticket as done") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsHandleFailureException> { fixture.shepherd.run() }
                    fixture.fakeStatusUpdater.markDoneCalled shouldBe false
                }

                it("THEN does NOT create an executor for the second part") {
                    val fixture = createFixture(parts, results, outFactory)
                    shouldThrow<TsHandleFailureException> { fixture.shepherd.run() }
                    fixture.fakeFactory.createdForParts.size shouldBe 1
                }
            }
        }

        describe("GIVEN a plan where a part returns FailedToConverge") {
            val parts = listOf(createTestPart("part-1"))
            val results = listOf(PartResult.FailedToConverge("exceeded max iterations"))

            describe("WHEN run() is called") {

                it("THEN delegates to FailedToExecutePlanUseCase") {
                    val fixture = createFixture(parts, results, outFactory)
                    val exception = shouldThrow<TsHandleFailureException> { fixture.shepherd.run() }
                    exception.partResult shouldBe PartResult.FailedToConverge("exceeded max iterations")
                }
            }
        }

        describe("GIVEN a plan where a part returns AgentCrashed") {
            val parts = listOf(createTestPart("part-1"))
            val results = listOf(PartResult.AgentCrashed("segfault"))

            describe("WHEN run() is called") {

                it("THEN delegates to FailedToExecutePlanUseCase") {
                    val fixture = createFixture(parts, results, outFactory)
                    val exception = shouldThrow<TsHandleFailureException> { fixture.shepherd.run() }
                    exception.partResult shouldBe PartResult.AgentCrashed("segfault")
                }
            }
        }

        describe("GIVEN a plan with no parts (empty plan)") {

            describe("WHEN run() is called") {

                it("THEN still performs final commit") {
                    val fixture = createFixture(emptyList(), emptyList(), outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeFinalCommit.commitIfDirtyCalled shouldBe true
                }

                it("THEN exits with code 0") {
                    val fixture = createFixture(emptyList(), emptyList(), outFactory)
                    val exception = shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    exception.exitCode shouldBe 0
                }
            }
        }

        describe("GIVEN a part with 1 sub-part (doer only)") {
            val singleSubPartPart = Part(
                name = "single-subpart",
                phase = Phase.EXECUTION,
                description = "part with doer only",
                subParts = listOf(SubPart(name = "impl", role = "DOER", agentType = "ClaudeCode", model = "sonnet")),
            )

            describe("WHEN run() is called") {

                it("THEN factory receives the part with exactly 1 sub-part") {
                    val fixture = createFixture(listOf(singleSubPartPart), listOf(PartResult.Completed), outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeFactory.createdForParts[0].subParts.size shouldBe 1
                }
            }
        }

        describe("GIVEN a part with 2 sub-parts (doer + reviewer)") {
            val twoSubPartsPart = Part(
                name = "with-reviewer",
                phase = Phase.EXECUTION,
                description = "part with doer and reviewer",
                subParts = listOf(
                    SubPart(name = "impl", role = "DOER", agentType = "ClaudeCode", model = "sonnet"),
                    SubPart(name = "review", role = "REVIEWER", agentType = "ClaudeCode", model = "opus"),
                ),
            )

            describe("WHEN run() is called") {

                it("THEN factory receives the part with exactly 2 sub-parts") {
                    val fixture = createFixture(listOf(twoSubPartsPart), listOf(PartResult.Completed), outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeFactory.createdForParts[0].subParts.size shouldBe 2
                }

                it("THEN factory receives the reviewer sub-part as second element") {
                    val fixture = createFixture(listOf(twoSubPartsPart), listOf(PartResult.Completed), outFactory)
                    shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
                    fixture.fakeFactory.createdForParts[0].subParts[1].role shouldBe "REVIEWER"
                }
            }
        }

        describe("GIVEN activeExecutor tracking during execution") {
            describe("WHEN a part is being executed") {

                it("THEN activeExecutor is non-null during execution and null after") {
                    val capturedActiveExecutors = mutableListOf<PartExecutor?>()
                    val parts = listOf(createTestPart("part-1"))
                    val currentState = CurrentState(parts = mutableListOf())

                    // Build shepherd with a factory that captures activeExecutor during execute()
                    lateinit var shepherd: TicketShepherd
                    val capturingFactory = PartExecutorFactory { _ ->
                        object : PartExecutor {
                            override suspend fun execute(): PartResult {
                                capturedActiveExecutors.add(shepherd.activeExecutor)
                                return PartResult.Completed
                            }
                        }
                    }

                    val deps = TicketShepherdDeps(
                        setupPlanUseCase = TsSetupPlanUseCase(parts),
                        failedToExecutePlanUseCase = TsFailedToExecutePlanUseCase(),
                        interruptHandler = TsInterruptHandler(),
                        allSessionsKiller = TsAllSessionsKiller(),
                        partExecutorFactory = capturingFactory,
                        consoleOutput = TsConsoleOutput(),
                        processExiter = TsProcessExiter(),
                        finalCommitUseCase = TsFinalCommitUseCase(),
                        ticketStatusUpdater = TsTicketStatusUpdater(),
                        aiOutputStructure = testAiOutputStructure,
                        out = outFactory.getOutForClass(TicketShepherd::class),
                        ticketId = "test-ticket-42",
                    )

                    shepherd = TicketShepherd(
                        deps = deps,
                        currentState = currentState,
                        originatingBranch = "main",
                        tryNumber = 1,
                    )

                    shouldThrow<TsProcessExitException> { shepherd.run() }

                    // During execution, activeExecutor was the executor itself (non-null)
                    capturedActiveExecutors.size shouldBe 1
                    capturedActiveExecutors[0] shouldNotBe null
                    // After execution, activeExecutor should be null
                    shepherd.activeExecutor shouldBe null
                }
            }
        }

        describe("GIVEN success path ordering") {
            describe("WHEN all parts complete") {

                it("THEN steps execute in order: finalCommit -> markDone -> killSessions -> printGreen -> exit") {
                    val orderTracker = mutableListOf<String>()
                    val parts = listOf(createTestPart("part-1"))
                    val currentState = CurrentState(parts = mutableListOf())

                    val deps = TicketShepherdDeps(
                        setupPlanUseCase = TsSetupPlanUseCase(parts),
                        failedToExecutePlanUseCase = TsFailedToExecutePlanUseCase(),
                        interruptHandler = TsInterruptHandler(),
                        allSessionsKiller = AllSessionsKiller { orderTracker.add("kill_sessions") },
                        partExecutorFactory = TsPartExecutorFactory(mutableListOf(PartResult.Completed)),
                        consoleOutput = object : ConsoleOutput {
                            override fun printlnRed(message: String) {
                                // not used in success path
                            }
                            override fun printlnGreen(message: String) {
                                orderTracker.add("print_green")
                            }
                        },
                        processExiter = object : ProcessExiter {
                            override fun exit(code: Int): Nothing {
                                orderTracker.add("exit")
                                throw TsProcessExitException(code)
                            }
                        },
                        finalCommitUseCase = FinalCommitUseCase { orderTracker.add("final_commit") },
                        ticketStatusUpdater = TicketStatusUpdater { orderTracker.add("mark_done") },
                        aiOutputStructure = testAiOutputStructure,
                        out = outFactory.getOutForClass(TicketShepherd::class),
                        ticketId = "test-ticket-42",
                    )

                    val shepherd = TicketShepherd(
                        deps = deps,
                        currentState = currentState,
                        originatingBranch = "main",
                        tryNumber = 1,
                    )

                    shouldThrow<TsProcessExitException> { shepherd.run() }

                    orderTracker shouldBe listOf(
                        "final_commit",
                        "mark_done",
                        "kill_sessions",
                        "print_green",
                        "exit",
                    )
                }
            }
        }

        describe("GIVEN originatingBranch and tryNumber") {
            it("THEN they are accessible on the TicketShepherd instance") {
                val fixture = createFixture(emptyList(), emptyList(), outFactory)
                fixture.shepherd.originatingBranch shouldBe "main"
                fixture.shepherd.tryNumber shouldBe 1
            }
        }
    },
)
