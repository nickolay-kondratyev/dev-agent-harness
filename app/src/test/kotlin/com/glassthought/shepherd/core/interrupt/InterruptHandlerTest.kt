package com.glassthought.shepherd.core.interrupt

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.CurrentStatePersistence
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.state.SubPartStatus
import com.glassthought.shepherd.core.time.TestClock
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * Thrown by [FakeProcessExiter] to interrupt flow and capture the exit code.
 */
private class FakeProcessExitException(val exitCode: Int) : RuntimeException("FakeProcessExit(code=$exitCode)")

private class FakeProcessExiter : ProcessExiter {
    var exitCalled = false
        private set
    var capturedCode: Int? = null
        private set

    override fun exit(code: Int): Nothing {
        exitCalled = true
        capturedCode = code
        throw FakeProcessExitException(code)
    }
}

private class FakeConsoleOutput : ConsoleOutput {
    val printedMessages = mutableListOf<String>()

    override fun printlnRed(message: String) {
        printedMessages.add(message)
    }
}

private class FakeAllSessionsKiller : AllSessionsKiller {
    var killAllSessionsCalled = false
        private set

    override suspend fun killAllSessions() {
        killAllSessionsCalled = true
    }
}

private class FakeCurrentStatePersistence : CurrentStatePersistence {
    var flushedState: CurrentState? = null
        private set

    override suspend fun flush(state: CurrentState) {
        flushedState = state
    }
}

// ── Test Helpers ────────────────────────────────────────────────────────────

private fun createSubPart(name: String, status: SubPartStatus): SubPart {
    return SubPart(
        name = name,
        role = "TEST_ROLE",
        agentType = "TestAgent",
        model = "test-model",
        status = status,
    )
}

private fun createPart(name: String, subParts: List<SubPart>): Part {
    return Part(
        name = name,
        phase = Phase.EXECUTION,
        description = "test part",
        subParts = subParts,
    )
}

private data class TestFixture(
    val handler: InterruptHandlerImpl,
    val clock: TestClock,
    val fakeKiller: FakeAllSessionsKiller,
    val fakeConsole: FakeConsoleOutput,
    val fakeExiter: FakeProcessExiter,
    val fakePersistence: FakeCurrentStatePersistence,
    val currentState: CurrentState,
)

private fun createFixture(
    parts: List<Part> = emptyList(),
): TestFixture {
    val clock = TestClock()
    val fakeKiller = FakeAllSessionsKiller()
    val fakeConsole = FakeConsoleOutput()
    val fakeExiter = FakeProcessExiter()
    val fakePersistence = FakeCurrentStatePersistence()
    val currentState = CurrentState(parts = parts.toMutableList())

    val handler = InterruptHandlerImpl(
        clock = clock,
        allSessionsKiller = fakeKiller,
        currentState = currentState,
        currentStatePersistence = fakePersistence,
        consoleOutput = fakeConsole,
        processExiter = fakeExiter,
    )

    return TestFixture(
        handler = handler,
        clock = clock,
        fakeKiller = fakeKiller,
        fakeConsole = fakeConsole,
        fakeExiter = fakeExiter,
        fakePersistence = fakePersistence,
        currentState = currentState,
    )
}

// ── Tests ───────────────────────────────────────────────────────────────────

class InterruptHandlerTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN no prior Ctrl+C press") {
            describe("WHEN first signal is received") {
                it("THEN prints confirmation message in red") {
                    val fixture = createFixture()
                    fixture.handler.handleSignal()

                    fixture.fakeConsole.printedMessages.first() shouldContain
                        InterruptHandlerImpl.CONFIRMATION_MESSAGE
                }

                it("THEN does NOT kill sessions") {
                    val fixture = createFixture()
                    fixture.handler.handleSignal()

                    fixture.fakeKiller.killAllSessionsCalled shouldBe false
                }

                it("THEN does NOT exit the process") {
                    val fixture = createFixture()
                    fixture.handler.handleSignal()

                    fixture.fakeExiter.exitCalled shouldBe false
                }

                it("THEN does NOT flush state to disk") {
                    val fixture = createFixture()
                    fixture.handler.handleSignal()

                    fixture.fakePersistence.flushedState shouldBe null
                }
            }
        }

        describe("GIVEN first Ctrl+C was pressed") {
            describe("AND second Ctrl+C arrives within 2 seconds") {
                describe("WHEN the second signal is received") {
                    it("THEN kills all sessions") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(1.seconds)

                        shouldThrow<FakeProcessExitException> {
                            fixture.handler.handleSignal()
                        }

                        fixture.fakeKiller.killAllSessionsCalled shouldBe true
                    }

                    it("THEN flushes state to disk") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(1.seconds)

                        shouldThrow<FakeProcessExitException> {
                            fixture.handler.handleSignal()
                        }

                        fixture.fakePersistence.flushedState shouldBe fixture.currentState
                    }

                    it("THEN exits with non-zero code") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(1.seconds)

                        val exception = shouldThrow<FakeProcessExitException> {
                            fixture.handler.handleSignal()
                        }

                        exception.exitCode shouldBe 1
                    }
                }
            }

            describe("AND second Ctrl+C arrives at exactly 1999ms (just under 2 seconds)") {
                describe("WHEN the second signal is received") {
                    it("THEN triggers cleanup and exits") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(1999.milliseconds)

                        val exception = shouldThrow<FakeProcessExitException> {
                            fixture.handler.handleSignal()
                        }

                        exception.exitCode shouldBe 1
                    }
                }
            }

            describe("AND second Ctrl+C arrives after 2+ seconds") {
                describe("WHEN the second signal is received") {
                    it("THEN prints confirmation message again (treated as fresh first press)") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(3.seconds)

                        fixture.handler.handleSignal()

                        fixture.fakeConsole.printedMessages.size shouldBe 2
                    }

                    it("THEN does NOT kill sessions") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(3.seconds)

                        fixture.handler.handleSignal()

                        fixture.fakeKiller.killAllSessionsCalled shouldBe false
                    }

                    it("THEN does NOT exit the process") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(3.seconds)

                        fixture.handler.handleSignal()

                        fixture.fakeExiter.exitCalled shouldBe false
                    }
                }
            }

            describe("AND second Ctrl+C arrives at exactly 2 seconds") {
                describe("WHEN the second signal is received") {
                    it("THEN treats it as fresh first press (window expired)") {
                        val fixture = createFixture()
                        fixture.handler.handleSignal()
                        fixture.clock.advance(2.seconds)

                        fixture.handler.handleSignal()

                        fixture.fakeExiter.exitCalled shouldBe false
                        fixture.fakeConsole.printedMessages.size shouldBe 2
                    }
                }
            }
        }

        describe("GIVEN state has sub-parts with mixed statuses") {
            val parts = listOf(
                createPart(
                    "part-1",
                    listOf(
                        createSubPart("doer", SubPartStatus.IN_PROGRESS),
                        createSubPart("reviewer", SubPartStatus.NOT_STARTED),
                    ),
                ),
                createPart(
                    "part-2",
                    listOf(
                        createSubPart("doer", SubPartStatus.COMPLETED),
                    ),
                ),
                createPart(
                    "part-3",
                    listOf(
                        createSubPart("doer", SubPartStatus.IN_PROGRESS),
                        createSubPart("reviewer", SubPartStatus.FAILED),
                    ),
                ),
            )

            describe("WHEN cleanup is triggered via double Ctrl+C") {
                it("THEN marks only IN_PROGRESS sub-parts as FAILED") {
                    val fixture = createFixture(parts = parts)
                    fixture.handler.handleSignal()
                    fixture.clock.advance(500.milliseconds)

                    shouldThrow<FakeProcessExitException> {
                        fixture.handler.handleSignal()
                    }

                    val flushedState = fixture.fakePersistence.flushedState!!

                    // part-1 doer: IN_PROGRESS -> FAILED
                    flushedState.parts[0].subParts[0].status shouldBe SubPartStatus.FAILED
                    // part-1 reviewer: NOT_STARTED stays NOT_STARTED
                    flushedState.parts[0].subParts[1].status shouldBe SubPartStatus.NOT_STARTED
                    // part-2 doer: COMPLETED stays COMPLETED
                    flushedState.parts[1].subParts[0].status shouldBe SubPartStatus.COMPLETED
                    // part-3 doer: IN_PROGRESS -> FAILED
                    flushedState.parts[2].subParts[0].status shouldBe SubPartStatus.FAILED
                    // part-3 reviewer: FAILED stays FAILED
                    flushedState.parts[2].subParts[1].status shouldBe SubPartStatus.FAILED
                }
            }
        }

        describe("GIVEN state has no parts (empty state)") {
            describe("WHEN cleanup is triggered via double Ctrl+C") {
                it("THEN exits without errors") {
                    val fixture = createFixture(parts = emptyList())
                    fixture.handler.handleSignal()
                    fixture.clock.advance(500.milliseconds)

                    val exception = shouldThrow<FakeProcessExitException> {
                        fixture.handler.handleSignal()
                    }

                    exception.exitCode shouldBe 1
                }

                it("THEN still flushes state to disk") {
                    val fixture = createFixture(parts = emptyList())
                    fixture.handler.handleSignal()
                    fixture.clock.advance(500.milliseconds)

                    shouldThrow<FakeProcessExitException> {
                        fixture.handler.handleSignal()
                    }

                    fixture.fakePersistence.flushedState shouldBe fixture.currentState
                }
            }
        }

        describe("GIVEN expired first press followed by confirmed double press") {
            describe("WHEN third signal arrives within 2 seconds of second") {
                it("THEN triggers cleanup and exit (second signal became new first)") {
                    val fixture = createFixture()

                    // First press
                    fixture.handler.handleSignal()
                    // Wait 3 seconds (expired)
                    fixture.clock.advance(3.seconds)
                    // Second press (treated as new first)
                    fixture.handler.handleSignal()
                    // Within 2 seconds - this is the confirming press
                    fixture.clock.advance(1.seconds)

                    val exception = shouldThrow<FakeProcessExitException> {
                        fixture.handler.handleSignal()
                    }

                    exception.exitCode shouldBe 1
                }
            }
        }
    },
)
