package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.core.out.LogLevel
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRequest
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentResult
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner
import com.glassthought.shepherd.core.state.PartResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * Configurable fake for [NonInteractiveAgentRunner].
 *
 * Returns [configuredResult] and captures the request for assertion.
 */
internal class FakeNonInteractiveAgentRunner(
    private val configuredResult: NonInteractiveAgentResult,
) : NonInteractiveAgentRunner {
    var capturedRequest: NonInteractiveAgentRequest? = null
        private set

    override suspend fun run(request: NonInteractiveAgentRequest): NonInteractiveAgentResult {
        capturedRequest = request
        return configuredResult
    }
}

/**
 * Thrown by [FakeGitProcessRunner] when a command matches a configured failure keyword.
 */
internal class FakeGitCommandFailureException(keyword: String) :
    IllegalStateException("Fake git failure: command containing [$keyword] is configured to fail")

/**
 * A [ProcessRunner] fake that records all git commands and can be configured
 * to fail on specific commands.
 */
internal class FakeGitProcessRunner : ProcessRunner {
    val executedCommands = mutableListOf<List<String>>()
    private val failOnCommands = mutableSetOf<String>()

    /** Configure a git sub-command keyword that triggers failure (e.g., "commit", "checkout"). */
    fun failOnCommandContaining(keyword: String) {
        failOnCommands.add(keyword)
    }

    override suspend fun runProcess(vararg input: String?): String {
        val args = input.filterNotNull()
        executedCommands.add(args)

        for (keyword in failOnCommands) {
            if (args.any { it.contains(keyword) }) {
                throw FakeGitCommandFailureException(keyword)
            }
        }
        return ""
    }

    override suspend fun runScript(script: com.asgard.core.file.File): String {
        error("Not implemented in fake")
    }

    override suspend fun runProcessV2(
        timeout: kotlin.time.Duration,
        vararg input: String?,
    ): com.asgard.core.processRunner.ProcessResult {
        error("Not implemented in fake")
    }
}

// ── Test Fixture ────────────────────────────────────────────────────────────

private data class TestFixture(
    val useCase: TicketFailureLearningUseCaseImpl,
    val fakeAgentRunner: FakeNonInteractiveAgentRunner,
    val fakeProcessRunner: FakeGitProcessRunner,
    val ticketFile: Path,
    val tempDir: Path,
)

private fun createFixture(
    outFactory: OutFactory,
    agentResult: NonInteractiveAgentResult = NonInteractiveAgentResult.Success(AGENT_SUCCESS_OUTPUT),
    initialTicketContent: String = BASIC_TICKET_CONTENT,
    failOnGitKeyword: String? = null,
): TestFixture {
    val tempDir = Files.createTempDirectory("failure-learning-test")
    val ticketFile = tempDir.resolve("ticket.md")
    Files.writeString(ticketFile, initialTicketContent)

    val fakeAgentRunner = FakeNonInteractiveAgentRunner(agentResult)
    val fakeProcessRunner = FakeGitProcessRunner()
    if (failOnGitKeyword != null) {
        fakeProcessRunner.failOnCommandContaining(failOnGitKeyword)
    }

    val runContext = FailureLearningRunContext(
        ticketPath = ticketFile.toString(),
        tryNumber = 1,
        branchName = "nid_abc__dashboard__try-1",
        originatingBranch = "nid_abc__dashboard",
        aiOutDir = tempDir.resolve(".ai_out").toString(),
        workingDirectory = tempDir,
        workflowType = "with-planning",
        failedAt = "backend_impl/impl",
        iteration = "2/3",
        partsCompleted = listOf("frontend/setup", "backend/setup"),
    )

    val useCase = TicketFailureLearningUseCaseImpl(
        outFactory = outFactory,
        nonInteractiveAgentRunner = fakeAgentRunner,
        runContext = runContext,
        processRunner = fakeProcessRunner,
    )

    return TestFixture(useCase, fakeAgentRunner, fakeProcessRunner, ticketFile, tempDir)
}

private const val BASIC_TICKET_CONTENT = """# Implement Dashboard

## Description
Build the dashboard feature.
"""

private const val AGENT_SUCCESS_OUTPUT = """**Approach**: Attempted to implement the dashboard by modifying App.tsx
**Root Cause**: Test setup was missing mock data for the API endpoint
**Recommendations**: Add mock data fixtures before running integration tests"""

private val FAILED_WORKFLOW_RESULT = PartResult.FailedWorkflow("test step X failed")

// ── Tests ───────────────────────────────────────────────────────────────────

class TicketFailureLearningUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN agent succeeds (happy path)") {
            describe("WHEN recordFailureLearning is called with FailedWorkflow") {
                it("THEN appends TRY-N section with Previous Failed Attempts heading") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "## Previous Failed Attempts"
                }

                it("THEN includes TRY-1 sub-heading") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "### TRY-1"
                }

                it("THEN includes branch name in structured facts") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "- **Branch**: `nid_abc__dashboard__try-1`"
                }

                it("THEN includes workflow type") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "- **Workflow**: with-planning"
                }

                it("THEN includes failure type derived from PartResult variant") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "- **Failure type**: FailedWorkflow"
                }

                it("THEN includes failed-at and iteration") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "- **Failed at**: backend_impl/impl (iteration 2/3)"
                }

                it("THEN includes parts completed") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "- **Parts completed**: frontend/setup, backend/setup"
                }

                it("THEN includes agent-generated summary") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "**Approach**: Attempted to implement the dashboard"
                    updated shouldContain "**Root Cause**: Test setup was missing mock data"
                    updated shouldContain "**Recommendations**: Add mock data fixtures"
                }

                it("THEN preserves original ticket content") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "# Implement Dashboard"
                    updated shouldContain "Build the dashboard feature."
                }

                it("THEN commits on the try branch") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val commitCommands = fixture.fakeProcessRunner.executedCommands
                        .filter { it.contains("commit") }
                    commitCommands.size shouldBe 2 // try branch + propagation
                }

                it("THEN propagates to originating branch") {
                    val fixture = createFixture(outFactory)
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val checkoutCommands = fixture.fakeProcessRunner.executedCommands
                        .filter { it.contains("checkout") }
                    // checkout originating, checkout tryBranch -- ticketPath, checkout back to try
                    checkoutCommands.any { it.contains("nid_abc__dashboard") } shouldBe true
                }
            }
        }

        describe("GIVEN agent returns Failed result") {
            describe("WHEN recordFailureLearning is called") {
                it("THEN uses fallback summary").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        agentResult = NonInteractiveAgentResult.Failed(exitCode = 1, output = "agent error"),
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain TicketFailureLearningUseCaseImpl.FALLBACK_SUMMARY
                }

                it("THEN still includes structured facts").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        agentResult = NonInteractiveAgentResult.Failed(exitCode = 1, output = "agent error"),
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "### TRY-1"
                    updated shouldContain "- **Branch**: `nid_abc__dashboard__try-1`"
                }

                it("THEN still commits to try branch").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        agentResult = NonInteractiveAgentResult.Failed(exitCode = 1, output = "agent error"),
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    fixture.fakeProcessRunner.executedCommands
                        .any { it.contains("commit") } shouldBe true
                }
            }
        }

        describe("GIVEN agent times out") {
            describe("WHEN recordFailureLearning is called") {
                it("THEN uses fallback summary").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        agentResult = NonInteractiveAgentResult.TimedOut(output = "partial output"),
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain TicketFailureLearningUseCaseImpl.FALLBACK_SUMMARY
                }

                it("THEN does not include agent output in summary section").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        agentResult = NonInteractiveAgentResult.TimedOut(output = "partial output"),
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldNotContain "partial output"
                }
            }
        }

        describe("GIVEN ticket already has Previous Failed Attempts heading") {
            val existingTicketWithAttempts = """# Implement Dashboard

## Description
Build the dashboard feature.

## Previous Failed Attempts

### TRY-1

- **Branch**: `nid_abc__dashboard__try-0`
- **Workflow**: straightforward
- **Failure type**: AgentCrashed
- **Failed at**: frontend/impl (iteration 1/1)
- **Parts completed**: none

#### Summary

*Agent failed to produce summary — structured facts above are the only record for this try.*
"""

            describe("WHEN recordFailureLearning is called") {
                it("THEN appends new TRY-N under existing heading without duplicating it") {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        initialTicketContent = existingTicketWithAttempts,
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    // Should have exactly one occurrence of the heading
                    val headingCount = updated.split("## Previous Failed Attempts").size - 1
                    headingCount shouldBe 1
                }

                it("THEN preserves existing TRY section") {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        initialTicketContent = existingTicketWithAttempts,
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "`nid_abc__dashboard__try-0`"
                    updated shouldContain "### TRY-1"
                }
            }
        }

        describe("GIVEN git commit fails on try branch") {
            describe("WHEN recordFailureLearning is called") {
                it("THEN does not throw (non-fatal)").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        failOnGitKeyword = "commit",
                    )
                    // Should not throw
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)
                }

                it("THEN still updates the ticket file").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        failOnGitKeyword = "commit",
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "### TRY-1"
                }
            }
        }

        describe("GIVEN propagation to originating branch fails") {
            describe("WHEN recordFailureLearning is called") {
                it("THEN does not throw (non-fatal)").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        failOnGitKeyword = "checkout",
                    )
                    // Should not throw
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)
                }

                it("THEN still has ticket updated with TRY section").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val fixture = createFixture(
                        outFactory = outFactory,
                        failOnGitKeyword = "checkout",
                    )
                    fixture.useCase.recordFailureLearning(FAILED_WORKFLOW_RESULT)

                    val updated = Files.readString(fixture.ticketFile)
                    updated shouldContain "### TRY-1"
                }
            }
        }

        describe("GIVEN each PartResult variant") {
            describe("WHEN mapping to failure context") {
                it("THEN FailedWorkflow maps to failureType FailedWorkflow") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(
                        PartResult.FailedWorkflow("some reason"),
                    )
                    context.failureType shouldBe "FailedWorkflow"
                }

                it("THEN AgentCrashed maps to failureType AgentCrashed") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(
                        PartResult.AgentCrashed("crash details"),
                    )
                    context.failureType shouldBe "AgentCrashed"
                }

                it("THEN FailedToConverge maps to failureType FailedToConverge") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(
                        PartResult.FailedToConverge("convergence summary"),
                    )
                    context.failureType shouldBe "FailedToConverge"
                }

                it("THEN Completed maps to failureType Completed") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(PartResult.Completed)
                    context.failureType shouldBe "Completed"
                }

                it("THEN workflowType comes from run context") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(FAILED_WORKFLOW_RESULT)
                    context.workflowType shouldBe "with-planning"
                }

                it("THEN failedAt comes from run context") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(FAILED_WORKFLOW_RESULT)
                    context.failedAt shouldBe "backend_impl/impl"
                }

                it("THEN iteration comes from run context") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(FAILED_WORKFLOW_RESULT)
                    context.iteration shouldBe "2/3"
                }

                it("THEN partsCompleted comes from run context") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(FAILED_WORKFLOW_RESULT)
                    context.partsCompleted shouldBe listOf("frontend/setup", "backend/setup")
                }
            }
        }

        describe("GIVEN agent instructions assembly") {
            describe("WHEN assembleAgentInstructions is called") {
                it("THEN includes failure facts") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(FAILED_WORKFLOW_RESULT)
                    val instructions = fixture.useCase.assembleAgentInstructions(context)

                    instructions shouldContain "Try number: 1"
                    instructions shouldContain "Branch: nid_abc__dashboard__try-1"
                    instructions shouldContain "Workflow: with-planning"
                    instructions shouldContain "Failure type: FailedWorkflow"
                }

                it("THEN includes artifacts directory path") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(FAILED_WORKFLOW_RESULT)
                    val instructions = fixture.useCase.assembleAgentInstructions(context)

                    instructions shouldContain ".ai_out"
                }

                it("THEN includes expected output format") {
                    val fixture = createFixture(outFactory)
                    val context = fixture.useCase.mapToFailureContext(FAILED_WORKFLOW_RESULT)
                    val instructions = fixture.useCase.assembleAgentInstructions(context)

                    instructions shouldContain "**Approach**"
                    instructions shouldContain "**Root Cause**"
                    instructions shouldContain "**Recommendations**"
                }
            }
        }

        describe("GIVEN buildTrySection") {
            describe("WHEN partsCompleted is empty") {
                it("THEN shows 'none' for parts completed") {
                    val tempDir = Files.createTempDirectory("build-try-section-test")
                    val ticketFile = tempDir.resolve("ticket.md")
                    Files.writeString(ticketFile, "# Test")

                    val runContext = FailureLearningRunContext(
                        ticketPath = ticketFile.toString(),
                        tryNumber = 2,
                        branchName = "try-2",
                        originatingBranch = "main",
                        aiOutDir = tempDir.resolve(".ai_out").toString(),
                        workingDirectory = tempDir,
                        workflowType = "straightforward",
                        failedAt = "impl",
                        iteration = "1/1",
                        partsCompleted = emptyList(),
                    )

                    val useCase = TicketFailureLearningUseCaseImpl(
                        outFactory = outFactory,
                        nonInteractiveAgentRunner = FakeNonInteractiveAgentRunner(
                            NonInteractiveAgentResult.Success(""),
                        ),
                        runContext = runContext,
                        processRunner = FakeGitProcessRunner(),
                    )

                    val context = PartResultFailureContext(
                        workflowType = "straightforward",
                        failureType = "AgentCrashed",
                        failedAt = "impl",
                        iteration = "1/1",
                        partsCompleted = emptyList(),
                    )

                    val section = useCase.buildTrySection(context, "some summary")
                    section shouldContain "- **Parts completed**: none"
                }
            }
        }
    },
)
