package com.glassthought.shepherd.core

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.interrupt.InterruptHandlerImpl
import com.glassthought.shepherd.core.time.TestClock
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path

// ── Test Fakes ─────────────────────────────────────────────────────────

private class FakeConsoleOutput : ConsoleOutput {
    val messages = mutableListOf<String>()
    override fun printlnRed(message: String) {
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
        val testAiOutputStructure = AiOutputStructure(
            repoRoot = Path.of("/tmp/test-repo"),
            branch = "test-branch",
        )

        describe("GIVEN a TicketShepherdCreatorImpl with test dependencies") {

            describe("WHEN create() is called") {

                it("THEN returns a result with an InterruptHandlerImpl") {
                    val killerFactory = TrackingKillerFactory()
                    val creator = TicketShepherdCreatorImpl(
                        shepherdContext = createTestShepherdContext(),
                        aiOutputStructure = testAiOutputStructure,
                        clock = TestClock(),
                        consoleOutput = FakeConsoleOutput(),
                        processExiter = FakeProcessExiter(),
                        allSessionsKillerFactory = killerFactory.create(),
                    )

                    val result = creator.create()

                    result.interruptHandler.shouldBeInstanceOf<InterruptHandlerImpl>()
                }

                it("THEN returns an initialized CurrentState with empty parts") {
                    val killerFactory = TrackingKillerFactory()
                    val creator = TicketShepherdCreatorImpl(
                        shepherdContext = createTestShepherdContext(),
                        aiOutputStructure = testAiOutputStructure,
                        clock = TestClock(),
                        consoleOutput = FakeConsoleOutput(),
                        processExiter = FakeProcessExiter(),
                        allSessionsKillerFactory = killerFactory.create(),
                    )

                    val result = creator.create()

                    result.currentState.parts shouldBe mutableListOf()
                }

                it("THEN returns a non-null CurrentStatePersistence") {
                    val killerFactory = TrackingKillerFactory()
                    val creator = TicketShepherdCreatorImpl(
                        shepherdContext = createTestShepherdContext(),
                        aiOutputStructure = testAiOutputStructure,
                        clock = TestClock(),
                        consoleOutput = FakeConsoleOutput(),
                        processExiter = FakeProcessExiter(),
                        allSessionsKillerFactory = killerFactory.create(),
                    )

                    val result = creator.create()

                    result.currentStatePersistence shouldNotBe null
                }

                it("THEN invokes the AllSessionsKiller factory") {
                    val killerFactory = TrackingKillerFactory()
                    val creator = TicketShepherdCreatorImpl(
                        shepherdContext = createTestShepherdContext(),
                        aiOutputStructure = testAiOutputStructure,
                        clock = TestClock(),
                        consoleOutput = FakeConsoleOutput(),
                        processExiter = FakeProcessExiter(),
                        allSessionsKillerFactory = killerFactory.create(),
                    )

                    creator.create()

                    killerFactory.factoryInvoked shouldBe true
                }
            }

            describe("AND the InterruptHandler is installed") {
                describe("WHEN a SIGINT signal is simulated on the returned handler") {

                    it("THEN the handler responds with confirmation message (proving install wired correctly)") {
                        val fakeConsole = FakeConsoleOutput()
                        val killerFactory = TrackingKillerFactory()
                        val creator = TicketShepherdCreatorImpl(
                            shepherdContext = createTestShepherdContext(),
                            aiOutputStructure = testAiOutputStructure,
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
        ),
    )
    val infra = com.glassthought.shepherd.core.initializer.Infra(
        outFactory = outFactory,
        tmux = tmuxInfra,
        claudeCode = claudeCodeInfra,
    )
    return com.glassthought.shepherd.core.initializer.data.ShepherdContext(infra = infra)
}
