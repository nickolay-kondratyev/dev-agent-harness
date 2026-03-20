package com.glassthought.shepherd.core.supporting.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import io.kotest.matchers.shouldBe

// ── Test Constants ──────────────────────────────────────────────────────

private val TEST_FAILURE_CONTEXT = GitFailureContext(
    partName = "test-part",
    subPartName = "test-sub",
    iterationNumber = 1,
)

// ── Tests ───────────────────────────────────────────────────────────────

class GitStagingCommitHelperTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("stageAll") {
            describe("GIVEN git add -A succeeds") {
                it("THEN executes git add -A") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand("git", "add", "-A", result = Result.success(""))
                    }
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    helper.stageAll(TEST_FAILURE_CONTEXT)

                    runner.executedCommands[0] shouldBe "git add -A"
                }
            }

            describe("GIVEN git add -A fails") {
                it("THEN delegates to GitOperationFailureUseCase with correct command") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "add", "-A",
                            result = Result.failure(RuntimeException("fatal: not a git repo")),
                        )
                    }
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        helper.stageAll(TEST_FAILURE_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedCommand shouldBe listOf("git", "add", "-A")
                }

                it("THEN passes error output to failure use case") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "add", "-A",
                            result = Result.failure(RuntimeException("fatal: not a git repo")),
                        )
                    }
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        helper.stageAll(TEST_FAILURE_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedErrorOutput shouldBe "fatal: not a git repo"
                }

                it("THEN passes the provided failure context") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "add", "-A",
                            result = Result.failure(RuntimeException("error")),
                        )
                    }
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        helper.stageAll(TEST_FAILURE_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedContext shouldBe TEST_FAILURE_CONTEXT
                }
            }
        }

        describe("hasStagedChanges") {
            describe("GIVEN git diff --cached --quiet exits 0 (no changes)") {
                it("THEN returns false") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand("git", "diff", "--cached", "--quiet", result = Result.success(""))
                    }
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    helper.hasStagedChanges() shouldBe false
                }
            }

            describe("GIVEN git diff --cached --quiet exits non-zero (changes exist)") {
                it("THEN returns true") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "diff", "--cached", "--quiet",
                            result = Result.failure(RuntimeException("exit code 1")),
                        )
                    }
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    helper.hasStagedChanges() shouldBe true
                }
            }
        }

        describe("commit") {
            describe("GIVEN git commit succeeds") {
                it("THEN executes the commit command with provided args") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand("git", "commit", "-m", "test message", result = Result.success(""))
                    }
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    helper.commit("commit", "-m", "test message", failureContext = TEST_FAILURE_CONTEXT)

                    runner.executedCommands[0] shouldBe "git commit -m test message"
                }
            }

            describe("GIVEN git commit with author succeeds") {
                it("THEN includes author in command") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "commit", "--author=Bot <bot@test.com>", "-m", "msg",
                            result = Result.success(""),
                        )
                    }
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    )

                    helper.commit(
                        "commit", "--author=Bot <bot@test.com>", "-m", "msg",
                        failureContext = TEST_FAILURE_CONTEXT,
                    )

                    runner.executedCommands[0] shouldBe
                        "git commit --author=Bot <bot@test.com> -m msg"
                }
            }

            describe("GIVEN git commit fails") {
                it("THEN delegates to GitOperationFailureUseCase") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "commit", "-m", "msg",
                            result = Result.failure(RuntimeException("fatal: could not commit")),
                        )
                    }
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        helper.commit("commit", "-m", "msg", failureContext = TEST_FAILURE_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedCommand shouldBe listOf("git", "commit", "-m", "msg")
                }

                it("THEN passes error output to failure use case") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "commit", "-m", "msg",
                            result = Result.failure(RuntimeException("fatal: could not commit")),
                        )
                    }
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        helper.commit("commit", "-m", "msg", failureContext = TEST_FAILURE_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedErrorOutput shouldBe "fatal: could not commit"
                }

                it("THEN passes the provided failure context") {
                    val runner = RecordingFakeProcessRunner().apply {
                        onCommand(
                            "git", "commit", "-m", "msg",
                            result = Result.failure(RuntimeException("error")),
                        )
                    }
                    val fakeFailure = FakeGitOperationFailureUseCase()
                    val helper = GitStagingCommitHelper(
                        processRunner = runner,
                        gitOperationFailureUseCase = fakeFailure,
                    )

                    try {
                        helper.commit("commit", "-m", "msg", failureContext = TEST_FAILURE_CONTEXT)
                    } catch (_: FakeGitFailureException) {
                        // Expected
                    }

                    fakeFailure.capturedContext shouldBe TEST_FAILURE_CONTEXT
                }
            }
        }

        describe("GIVEN workingDir is specified") {
            it("THEN stageAll prepends -C <dir> to the command") {
                val runner = RecordingFakeProcessRunner().apply {
                    onCommand("git", "-C", "/my/repo", "add", "-A", result = Result.success(""))
                }
                val helper = GitStagingCommitHelper(
                    processRunner = runner,
                    gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    gitCommandBuilder = GitCommandBuilder(java.nio.file.Path.of("/my/repo")),
                )

                helper.stageAll(TEST_FAILURE_CONTEXT)

                runner.executedCommands[0] shouldBe "git -C /my/repo add -A"
            }

            it("THEN hasStagedChanges prepends -C <dir> to the command") {
                val runner = RecordingFakeProcessRunner().apply {
                    onCommand(
                        "git", "-C", "/my/repo", "diff", "--cached", "--quiet",
                        result = Result.success(""),
                    )
                }
                val helper = GitStagingCommitHelper(
                    processRunner = runner,
                    gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    gitCommandBuilder = GitCommandBuilder(java.nio.file.Path.of("/my/repo")),
                )

                helper.hasStagedChanges()

                runner.executedCommands[0] shouldBe "git -C /my/repo diff --cached --quiet"
            }

            it("THEN commit prepends -C <dir> to the command") {
                val runner = RecordingFakeProcessRunner().apply {
                    onCommand(
                        "git", "-C", "/my/repo", "commit", "-m", "msg",
                        result = Result.success(""),
                    )
                }
                val helper = GitStagingCommitHelper(
                    processRunner = runner,
                    gitOperationFailureUseCase = FakeGitOperationFailureUseCase(),
                    gitCommandBuilder = GitCommandBuilder(java.nio.file.Path.of("/my/repo")),
                )

                helper.commit("commit", "-m", "msg", failureContext = TEST_FAILURE_CONTEXT)

                runner.executedCommands[0] shouldBe "git -C /my/repo commit -m msg"
            }
        }
    },
)
