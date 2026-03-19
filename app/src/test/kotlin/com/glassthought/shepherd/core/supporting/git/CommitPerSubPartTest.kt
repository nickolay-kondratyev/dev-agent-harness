package com.glassthought.shepherd.core.supporting.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.data.AgentType
import io.kotest.matchers.shouldBe

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * Captures calls to [GitOperationFailureUseCase.handleGitFailure] for assertion.
 *
 * Throws [FakeGitFailureException] to halt execution (simulating the real use case
 * which terminates the process via [FailedToExecutePlanUseCase]).
 */
internal class FakeGitOperationFailureUseCase : GitOperationFailureUseCase {
    var capturedCommand: List<String>? = null
        private set
    var capturedErrorOutput: String? = null
        private set
    var capturedContext: GitFailureContext? = null
        private set

    override suspend fun handleGitFailure(
        gitCommand: List<String>,
        errorOutput: String,
        context: GitFailureContext,
    ) {
        capturedCommand = gitCommand
        capturedErrorOutput = errorOutput
        capturedContext = context
        throw FakeGitFailureException()
    }
}

internal class FakeGitFailureException : RuntimeException("FakeGitFailure")

/**
 * Records every command executed via [runProcess] in order, for verification.
 *
 * Extends [FakeProcessRunner] (from GitOperationFailureUseCaseImplTest) with
 * ordered command tracking.
 */
internal class RecordingFakeProcessRunner : com.asgard.core.processRunner.ProcessRunner {
    private val responses = mutableMapOf<String, Result<String>>()
    val executedCommands = mutableListOf<String>()

    fun onCommand(vararg args: String, result: Result<String>) {
        responses[args.toList().joinToString(" ")] = result
    }

    override suspend fun runProcess(vararg input: String?): String {
        val key = input.filterNotNull().joinToString(" ")
        executedCommands.add(key)
        val result = responses[key]
            ?: error("Fake: unrecognized command [$key]")
        return result.getOrThrow()
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

// ── Test Constants ──────────────────────────────────────────────────────────

private const val HOST_USERNAME = "testuser"
private const val GIT_USER_EMAIL = "testuser@example.com"

private val DEFAULT_CONTEXT = SubPartDoneContext(
    partName = "ui_design",
    subPartName = "impl",
    subPartRole = "doer",
    result = "completed",
    hasReviewer = true,
    currentIteration = 1,
    maxIterations = 3,
    agentType = AgentType.CLAUDE_CODE,
    model = "sonnet",
)

private val CONTEXT_WITHOUT_REVIEWER = DEFAULT_CONTEXT.copy(
    hasReviewer = false,
    currentIteration = 0,
    maxIterations = 0,
)

// ── Tests ───────────────────────────────────────────────────────────────────

class CommitPerSubPartTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN changes exist after sub-part done") {
            fun createRunnerWithChanges(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand("git", "add", "-A", result = Result.success(""))
                    // git diff --cached --quiet throws on non-zero → changes exist
                    onCommand(
                        "git", "diff", "--cached", "--quiet",
                        result = Result.failure(RuntimeException("exit code 1")),
                    )
                    onCommand(
                        "git", "commit",
                        "--author=CC_sonnet_WITH-testuser <testuser@example.com>",
                        "-m",
                        "[shepherd] ui_design/impl — completed (iteration 1/3)",
                        result = Result.success(""),
                    )
                }
            }

            describe("WHEN onSubPartDone is called") {
                it("THEN executes git add -A first") {
                    val runner = createRunnerWithChanges()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(DEFAULT_CONTEXT)

                    runner.executedCommands[0] shouldBe "git add -A"
                }

                it("THEN checks for staged changes via git diff --cached --quiet") {
                    val runner = createRunnerWithChanges()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(DEFAULT_CONTEXT)

                    runner.executedCommands[1] shouldBe "git diff --cached --quiet"
                }

                it("THEN creates commit with correct author and message") {
                    val runner = createRunnerWithChanges()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(DEFAULT_CONTEXT)

                    runner.executedCommands[2] shouldBe
                        "git commit --author=CC_sonnet_WITH-testuser <testuser@example.com>" +
                        " -m [shepherd] ui_design/impl — completed (iteration 1/3)"
                }

                it("THEN executes exactly 3 git commands") {
                    val runner = createRunnerWithChanges()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(DEFAULT_CONTEXT)

                    runner.executedCommands.size shouldBe 3
                }
            }
        }

        describe("GIVEN no changes after sub-part done (empty diff)") {
            fun createRunnerWithNoChanges(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand("git", "add", "-A", result = Result.success(""))
                    // git diff --cached --quiet exits 0 → no changes
                    onCommand(
                        "git", "diff", "--cached", "--quiet",
                        result = Result.success(""),
                    )
                }
            }

            describe("WHEN onSubPartDone is called") {
                it("THEN skips commit (only 2 git commands executed)") {
                    val runner = createRunnerWithNoChanges()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(DEFAULT_CONTEXT)

                    runner.executedCommands.size shouldBe 2
                }

                it("THEN does not invoke git commit") {
                    val runner = createRunnerWithNoChanges()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(DEFAULT_CONTEXT)

                    runner.executedCommands.none { it.startsWith("git commit") } shouldBe true
                }
            }
        }

        describe("GIVEN git add fails") {
            fun createRunnerWithAddFailure(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand(
                        "git", "add", "-A",
                        result = Result.failure(RuntimeException("fatal: not a git repository")),
                    )
                }
            }

            describe("WHEN onSubPartDone is called") {
                it("THEN delegates to GitOperationFailureUseCase") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = createRunnerWithAddFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    try {
                        strategy.onSubPartDone(DEFAULT_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected — fake throws to halt execution
                    }

                    fakeFailure.capturedCommand shouldBe listOf("git", "add", "-A")
                }

                it("THEN passes error output to failure use case") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = createRunnerWithAddFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    try {
                        strategy.onSubPartDone(DEFAULT_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedErrorOutput shouldBe "fatal: not a git repository"
                }

                it("THEN passes correct GitFailureContext") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = createRunnerWithAddFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    try {
                        strategy.onSubPartDone(DEFAULT_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedContext shouldBe GitFailureContext(
                        partName = "ui_design",
                        subPartName = "impl",
                        iterationNumber = 1,
                    )
                }
            }
        }

        describe("GIVEN git commit fails") {
            fun createRunnerWithCommitFailure(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand("git", "add", "-A", result = Result.success(""))
                    onCommand(
                        "git", "diff", "--cached", "--quiet",
                        result = Result.failure(RuntimeException("exit code 1")),
                    )
                    onCommand(
                        "git", "commit",
                        "--author=CC_sonnet_WITH-testuser <testuser@example.com>",
                        "-m",
                        "[shepherd] ui_design/impl — completed (iteration 1/3)",
                        result = Result.failure(RuntimeException("fatal: could not commit")),
                    )
                }
            }

            describe("WHEN onSubPartDone is called") {
                it("THEN delegates to GitOperationFailureUseCase") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = createRunnerWithCommitFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    try {
                        strategy.onSubPartDone(DEFAULT_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedCommand!!.first() shouldBe "git"
                    fakeFailure.capturedCommand!!.contains("commit") shouldBe true
                }

                it("THEN passes commit error output to failure use case") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = createRunnerWithCommitFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    try {
                        strategy.onSubPartDone(DEFAULT_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedErrorOutput shouldBe "fatal: could not commit"
                }
            }
        }

        describe("GIVEN workingDir is specified") {
            fun createRunnerWithWorkingDir(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand(
                        "git", "-C", "/some/repo", "add", "-A",
                        result = Result.success(""),
                    )
                    onCommand(
                        "git", "-C", "/some/repo", "diff", "--cached", "--quiet",
                        result = Result.failure(RuntimeException("exit code 1")),
                    )
                    onCommand(
                        "git", "-C", "/some/repo", "commit",
                        "--author=CC_sonnet_WITH-testuser <testuser@example.com>",
                        "-m",
                        "[shepherd] ui_design/impl — completed (iteration 1/3)",
                        result = Result.success(""),
                    )
                }
            }

            describe("WHEN onSubPartDone is called") {
                it("THEN all git commands include -C <workingDir>") {
                    val runner = createRunnerWithWorkingDir()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                        workingDir = java.nio.file.Path.of("/some/repo"),
                    )

                    strategy.onSubPartDone(DEFAULT_CONTEXT)

                    runner.executedCommands.all { it.contains("-C /some/repo") } shouldBe true
                }
            }
        }

        describe("GIVEN context without reviewer") {
            fun createRunnerForNoReviewer(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand("git", "add", "-A", result = Result.success(""))
                    onCommand(
                        "git", "diff", "--cached", "--quiet",
                        result = Result.failure(RuntimeException("exit code 1")),
                    )
                    // Without reviewer, commit message omits iteration info
                    onCommand(
                        "git", "commit",
                        "--author=CC_sonnet_WITH-testuser <testuser@example.com>",
                        "-m",
                        "[shepherd] ui_design/impl — completed",
                        result = Result.success(""),
                    )
                }
            }

            describe("WHEN onSubPartDone is called") {
                it("THEN commit message omits iteration suffix") {
                    val runner = createRunnerForNoReviewer()
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(CONTEXT_WITHOUT_REVIEWER)

                    // If the commit command was recognized and executed, the message format is correct
                    runner.executedCommands.size shouldBe 3
                }
            }
        }

        describe("GIVEN PI agent type") {
            fun createRunnerForPiAgent(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand("git", "add", "-A", result = Result.success(""))
                    onCommand(
                        "git", "diff", "--cached", "--quiet",
                        result = Result.failure(RuntimeException("exit code 1")),
                    )
                    onCommand(
                        "git", "commit",
                        "--author=PI_glm-5_WITH-testuser <testuser@example.com>",
                        "-m",
                        "[shepherd] planning/plan — completed (iteration 1/3)",
                        result = Result.success(""),
                    )
                }
            }

            describe("WHEN onSubPartDone is called") {
                it("THEN uses PI agent short code in author") {
                    val runner = createRunnerForPiAgent()
                    val context = DEFAULT_CONTEXT.copy(
                        partName = "planning",
                        subPartName = "plan",
                        agentType = AgentType.PI,
                        model = "glm-5",
                    )
                    val strategy = CommitPerSubPart(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                        hostUsername = HOST_USERNAME,
                        gitUserEmail = GIT_USER_EMAIL,
                    )

                    strategy.onSubPartDone(context)

                    // Commit command was recognized → author format is correct
                    runner.executedCommands.any { it.contains("PI_glm-5_WITH-testuser") } shouldBe true
                }
            }
        }
    },
)
