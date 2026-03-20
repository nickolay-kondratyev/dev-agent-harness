package com.glassthought.shepherd.usecase.finalcommit

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.supporting.git.FakeGitFailureException
import com.glassthought.shepherd.core.supporting.git.FakeGitOperationFailureUseCase
import com.glassthought.shepherd.core.supporting.git.GitFailureContext
import com.glassthought.shepherd.core.supporting.git.RecordingFakeProcessRunner
import io.kotest.matchers.shouldBe

// ── Test Constants ──────────────────────────────────────────────────────

private val EXPECTED_FAILURE_CONTEXT = GitFailureContext(
    partName = "final-commit",
    subPartName = "final-state",
    iterationNumber = 0,
)

// ── Tests ───────────────────────────────────────────────────────────────

class FinalCommitUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN working tree has uncommitted changes") {
            fun createRunnerWithChanges(): RecordingFakeProcessRunner {
                return RecordingFakeProcessRunner().apply {
                    onCommand("git", "add", "-A", result = Result.success(""))
                    // git diff --cached --quiet throws on non-zero → changes exist
                    onCommand(
                        "git", "diff", "--cached", "--quiet",
                        result = Result.failure(RuntimeException("exit code 1")),
                    )
                    onCommand(
                        "git", "commit", "-m", FinalCommitUseCaseImpl.COMMIT_MESSAGE,
                        result = Result.success(""),
                    )
                }
            }

            describe("WHEN commitIfDirty is called") {
                it("THEN executes git add -A first") {
                    val runner = createRunnerWithChanges()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    useCase.commitIfDirty()

                    runner.executedCommands[0] shouldBe "git add -A"
                }

                it("THEN checks for staged changes via git diff --cached --quiet") {
                    val runner = createRunnerWithChanges()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    useCase.commitIfDirty()

                    runner.executedCommands[1] shouldBe "git diff --cached --quiet"
                }

                it("THEN creates commit with shepherd final-state-commit message") {
                    val runner = createRunnerWithChanges()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    useCase.commitIfDirty()

                    runner.executedCommands[2] shouldBe
                        "git commit -m [shepherd] final-state-commit"
                }

                it("THEN executes exactly 3 git commands") {
                    val runner = createRunnerWithChanges()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    useCase.commitIfDirty()

                    runner.executedCommands.size shouldBe 3
                }
            }
        }

        describe("GIVEN working tree is clean (no changes)") {
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

            describe("WHEN commitIfDirty is called") {
                it("THEN skips commit (only 2 git commands executed)") {
                    val runner = createRunnerWithNoChanges()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    useCase.commitIfDirty()

                    runner.executedCommands.size shouldBe 2
                }

                it("THEN does not invoke git commit") {
                    val runner = createRunnerWithNoChanges()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    useCase.commitIfDirty()

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

            describe("WHEN commitIfDirty is called") {
                it("THEN delegates to GitOperationFailureUseCase") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = createRunnerWithAddFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        useCase.commitIfDirty()
                    } catch (_: FakeGitFailureException) {
                        // Expected — fake throws to halt execution
                    }

                    fakeFailure.capturedCommand shouldBe listOf("git", "add", "-A")
                }

                it("THEN passes error output to failure use case") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = createRunnerWithAddFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        useCase.commitIfDirty()
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedErrorOutput shouldBe "fatal: not a git repository"
                }

                it("THEN passes correct GitFailureContext") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = createRunnerWithAddFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        useCase.commitIfDirty()
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedContext shouldBe EXPECTED_FAILURE_CONTEXT
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
                        "git", "commit", "-m", FinalCommitUseCaseImpl.COMMIT_MESSAGE,
                        result = Result.failure(RuntimeException("fatal: could not commit")),
                    )
                }
            }

            describe("WHEN commitIfDirty is called") {
                it("THEN delegates to GitOperationFailureUseCase") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = createRunnerWithCommitFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        useCase.commitIfDirty()
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedCommand!!.first() shouldBe "git"
                    fakeFailure.capturedCommand!!.contains("commit") shouldBe true
                }

                it("THEN passes commit error output to failure use case") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = createRunnerWithCommitFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        useCase.commitIfDirty()
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedErrorOutput shouldBe "fatal: could not commit"
                }

                it("THEN passes correct GitFailureContext for commit failure") {
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val useCase = FinalCommitUseCaseImpl(
                        outFactory = outFactory,
                        processRunner = createRunnerWithCommitFailure(),
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        useCase.commitIfDirty()
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedContext shouldBe EXPECTED_FAILURE_CONTEXT
                }
            }
        }
    },
)
