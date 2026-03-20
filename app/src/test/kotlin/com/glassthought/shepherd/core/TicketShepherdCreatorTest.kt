package com.glassthought.shepherd.core

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.adapter.CallbackScriptsDir
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.interrupt.InterruptHandlerImpl
import com.glassthought.shepherd.core.state.CurrentStatePersistenceImpl
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.time.TestClock
import com.glassthought.shepherd.core.workflow.WorkflowDefinition
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

// ── Test Fakes ─────────────────────────────────────────────────────────

private class FakeConsoleOutput : ConsoleOutput {
    val messages = mutableListOf<String>()
    override fun printlnRed(message: String) {
        messages.add(message)
    }
    override fun printlnGreen(message: String) {
        messages.add(message)
    }
}

private class FakeProcessExitException(val exitCode: Int) : RuntimeException("FakeExit($exitCode)")

private class FakeProcessExiter : ProcessExiter {
    var exitCalled = false
        private set

    override fun exit(code: Int): Nothing {
        exitCalled = true
        throw FakeProcessExitException(code)
    }
}

private class FakeAllSessionsKiller : AllSessionsKiller {
    var killCalled = false
        private set

    override suspend fun killAllSessions() {
        killCalled = true
    }
}

/**
 * Tracks whether the [AllSessionsKiller] factory was invoked and with what.
 */
private class TrackingKillerFactory {
    var factoryInvoked = false
        private set
    val killer = FakeAllSessionsKiller()

    fun create(): (com.asgard.core.out.OutFactory) -> AllSessionsKiller = { _ ->
        factoryInvoked = true
        killer
    }
}

// ── Tests ──────────────────────────────────────────────────────────────

class TicketShepherdCreatorTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        // Shared test infrastructure
        val testTempDir = Files.createTempDirectory("creator-test")
        val testAiOutputStructure = AiOutputStructure(
            repoRoot = testTempDir,
            branch = "test-branch",
        )
        val testWorkflowDefinition = WorkflowDefinition(
            name = "test-straightforward",
            parts = listOf(
                Part(
                    name = "part_1",
                    phase = Phase.EXECUTION,
                    description = "test part",
                    subParts = listOf(
                        SubPart(name = "impl", role = "DOER", agentType = "ClaudeCode", model = "sonnet"),
                    ),
                ),
            ),
        )

        describe("GIVEN a TicketShepherdCreatorImpl with test dependencies") {

            describe("WHEN create() is called") {

                // DRY: shared creator construction for tests that don't need specific fakes
                val killerFactory = TrackingKillerFactory()
                val creator = TicketShepherdCreatorImpl(
                    shepherdContext = createTestShepherdContext(),
                    aiOutputStructure = testAiOutputStructure,
                    workflowDefinition = testWorkflowDefinition,
                    clock = TestClock(),
                    consoleOutput = FakeConsoleOutput(),
                    processExiter = FakeProcessExiter(),
                    allSessionsKillerFactory = killerFactory.create(),
                )
                val result = creator.create()

                it("THEN returns a result with an InterruptHandlerImpl") {
                    result.interruptHandler.shouldBeInstanceOf<InterruptHandlerImpl>()
                }

                it("THEN returns an initialized CurrentState with empty parts") {
                    result.currentState.parts shouldBe mutableListOf()
                }

                it("THEN returns a CurrentStatePersistenceImpl (correct wiring)") {
                    result.currentStatePersistence.shouldBeInstanceOf<CurrentStatePersistenceImpl>()
                }

                it("THEN invokes the AllSessionsKiller factory") {
                    killerFactory.factoryInvoked shouldBe true
                }
            }

            describe("WHEN create() result handler is invoked directly") {

                it("THEN the handler responds with confirmation message (proving wiring correctness)") {
                    val fakeConsole = FakeConsoleOutput()
                    val killerFactory = TrackingKillerFactory()
                    val creator = TicketShepherdCreatorImpl(
                        shepherdContext = createTestShepherdContext(),
                        aiOutputStructure = testAiOutputStructure,
                        workflowDefinition = testWorkflowDefinition,
                        clock = TestClock(),
                        consoleOutput = fakeConsole,
                        processExiter = FakeProcessExiter(),
                        allSessionsKillerFactory = killerFactory.create(),
                    )

                    val result = creator.create()

                    // Verify the handler is functional by calling handleSignal directly
                    (result.interruptHandler as InterruptHandlerImpl).handleSignal()

                    fakeConsole.messages.size shouldBe 1
                }
            }
        }

        describe("GIVEN a TicketShepherdCreatorImpl with a straightforward workflow") {

            describe("WHEN create() is called") {
                val tempDir = Files.createTempDirectory("ensure-structure-test")
                val aiOutputStructure = AiOutputStructure(
                    repoRoot = tempDir,
                    branch = "structure-test-branch",
                )
                val workflowDef = WorkflowDefinition(
                    name = "test-straightforward",
                    parts = listOf(
                        Part(
                            name = "part_1",
                            phase = Phase.EXECUTION,
                            description = "test part",
                            subParts = listOf(
                                SubPart(name = "impl", role = "DOER", agentType = "ClaudeCode", model = "sonnet"),
                                SubPart(name = "review", role = "REVIEWER", agentType = "ClaudeCode", model = "opus"),
                            ),
                        ),
                    ),
                )
                val creator = TicketShepherdCreatorImpl(
                    shepherdContext = createTestShepherdContext(),
                    aiOutputStructure = aiOutputStructure,
                    workflowDefinition = workflowDef,
                    clock = TestClock(),
                    consoleOutput = FakeConsoleOutput(),
                    processExiter = FakeProcessExiter(),
                    allSessionsKillerFactory = TrackingKillerFactory().create(),
                )
                creator.create()

                it("THEN creates the harness_private directory") {
                    Files.isDirectory(aiOutputStructure.harnessPrivateDir()) shouldBe true
                }

                it("THEN creates the shared/plan directory") {
                    Files.isDirectory(aiOutputStructure.sharedPlanDir()) shouldBe true
                }

                it("THEN creates the execution part directory with feedback subdirs") {
                    Files.isDirectory(aiOutputStructure.feedbackPendingDir("part_1")) shouldBe true
                    Files.isDirectory(aiOutputStructure.feedbackAddressedDir("part_1")) shouldBe true
                    Files.isDirectory(aiOutputStructure.feedbackRejectedDir("part_1")) shouldBe true
                }

                it("THEN creates the execution sub-part comm directories") {
                    Files.isDirectory(aiOutputStructure.executionCommInDir("part_1", "impl")) shouldBe true
                    Files.isDirectory(aiOutputStructure.executionCommOutDir("part_1", "impl")) shouldBe true
                    Files.isDirectory(aiOutputStructure.executionCommInDir("part_1", "review")) shouldBe true
                    Files.isDirectory(aiOutputStructure.executionCommOutDir("part_1", "review")) shouldBe true
                }

                it("THEN creates the execution sub-part private directories") {
                    Files.isDirectory(aiOutputStructure.executionSubPartPrivateDir("part_1", "impl")) shouldBe true
                    Files.isDirectory(aiOutputStructure.executionSubPartPrivateDir("part_1", "review")) shouldBe true
                }
            }
        }
    },
)

// ── Test Helper ────────────────────────────────────────────────────────

/**
 * Creates a minimal [com.glassthought.shepherd.core.initializer.data.ShepherdContext]
 * for unit testing TicketShepherdCreator.
 *
 * Uses the test outFactory from AsgardDescribeSpec's inherited outFactory would be ideal,
 * but since this is a standalone helper, we construct a no-op ShepherdContext.
 */
private fun createTestShepherdContext(): com.glassthought.shepherd.core.initializer.data.ShepherdContext {
    val outFactory = com.asgard.core.out.impl.NoOpOutFactory()
    val commandRunner = com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner()
    val communicator = com.glassthought.shepherd.core.agent.tmux.TmuxCommunicatorImpl(
        outFactory, commandRunner,
    )
    val sessionManager = com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager(
        outFactory, commandRunner, communicator,
    )
    val tmuxInfra = com.glassthought.shepherd.core.initializer.TmuxInfra(
        commandRunner = commandRunner,
        communicator = communicator,
        sessionManager = sessionManager,
    )
    val claudeCodeInfra = com.glassthought.shepherd.core.initializer.ClaudeCodeInfra(
        agentTypeAdapter = com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter.create(
            claudeProjectsDir = java.nio.file.Path.of("/tmp/test-claude-projects"),
            outFactory = outFactory,
            serverPort = 18080,
            callbackScriptsDir = CallbackScriptsDir.unvalidated("/tmp/test-callback-scripts"),
        ),
    )
    val infra = com.glassthought.shepherd.core.initializer.Infra(
        outFactory = outFactory,
        tmux = tmuxInfra,
        claudeCode = claudeCodeInfra,
    )
    val noOpRunner = com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner {
        com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentResult.Success(output = "")
    }
    return com.glassthought.shepherd.core.initializer.data.ShepherdContext(
        infra = infra,
        nonInteractiveAgentRunner = noOpRunner,
    )
}
