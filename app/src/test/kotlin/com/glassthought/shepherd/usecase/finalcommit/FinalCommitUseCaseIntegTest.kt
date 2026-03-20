package com.glassthought.shepherd.usecase.finalcommit

import com.asgard.core.processRunner.ProcessRunner
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.bucket.isIntegTestEnabled
import com.glassthought.shepherd.core.supporting.git.GitCommandBuilder
import com.glassthought.shepherd.core.supporting.git.GitFailureContext
import com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase
import io.kotest.core.annotation.EnabledIf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

// ── Integ test support ──────────────────────────────────────────────────

/**
 * No-op failure handler — integration tests verify the happy path only.
 * Git failures in a controlled temp repo are unexpected and should fail the test.
 */
private class ThrowingGitOperationFailureUseCase : GitOperationFailureUseCase {
    override suspend fun handleGitFailure(
        gitCommand: List<String>,
        errorOutput: String,
        context: GitFailureContext,
    ) {
        error("Unexpected git failure in integration test: ${gitCommand.joinToString(" ")} — $errorOutput")
    }
}

// ── Tests ───────────────────────────────────────────────────────────────

@EnabledIf(IntegTestCondition::class)
class FinalCommitUseCaseIntegTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN a real git repository with uncommitted changes").config(
            enabled = isIntegTestEnabled(),
        ) {
            describe("WHEN commitIfDirty is called") {
                it("THEN creates a commit with the shepherd message") {
                    val tempDir = Files.createTempDirectory("final-commit-integ")
                    try {
                        val processRunner = ProcessRunner.standard(outFactory)
                        initGitRepo(processRunner, tempDir)

                        // Create an uncommitted file
                        Files.writeString(tempDir.resolve("state.json"), "{}")

                        val useCase = FinalCommitUseCaseImpl(
                            outFactory = outFactory,
                            processRunner = processRunner,
                            gitOperationFailureUseCase = ThrowingGitOperationFailureUseCase(),
                            gitCommandBuilder = GitCommandBuilder(tempDir),
                        )

                        useCase.commitIfDirty()

                        // Verify commit exists with the expected message
                        val log = processRunner.runProcess(
                            "git", "-C", tempDir.toString(), "log", "--oneline", "-1",
                        )
                        log shouldContain "[shepherd] final-state-commit"
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }
            }
        }

        describe("GIVEN a real git repository with no changes").config(
            enabled = isIntegTestEnabled(),
        ) {
            describe("WHEN commitIfDirty is called") {
                it("THEN does not create a new commit") {
                    val tempDir = Files.createTempDirectory("final-commit-integ-clean")
                    try {
                        val processRunner = ProcessRunner.standard(outFactory)
                        initGitRepo(processRunner, tempDir)

                        // Get current commit count (just the initial commit)
                        val beforeCount = getCommitCount(processRunner, tempDir)

                        val useCase = FinalCommitUseCaseImpl(
                            outFactory = outFactory,
                            processRunner = processRunner,
                            gitOperationFailureUseCase = ThrowingGitOperationFailureUseCase(),
                            gitCommandBuilder = GitCommandBuilder(tempDir),
                        )

                        useCase.commitIfDirty()

                        val afterCount = getCommitCount(processRunner, tempDir)
                        afterCount shouldBe beforeCount
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }
            }
        }
    },
)

private suspend fun initGitRepo(processRunner: ProcessRunner, dir: Path) {
    processRunner.runProcess("git", "-C", dir.toString(), "init")
    processRunner.runProcess("git", "-C", dir.toString(), "config", "user.email", "test@test.com")
    processRunner.runProcess("git", "-C", dir.toString(), "config", "user.name", "Test")
    // Create initial commit so HEAD exists
    Files.writeString(dir.resolve("README.md"), "init")
    processRunner.runProcess("git", "-C", dir.toString(), "add", "-A")
    processRunner.runProcess("git", "-C", dir.toString(), "commit", "-m", "initial")
}

private suspend fun getCommitCount(processRunner: ProcessRunner, dir: Path): Int {
    val output = processRunner.runProcess(
        "git", "-C", dir.toString(), "rev-list", "--count", "HEAD",
    )
    return output.trim().toInt()
}

/**
 * Kotest [EnabledIf] condition that delegates to [isIntegTestEnabled].
 */
class IntegTestCondition : io.kotest.core.annotation.EnabledCondition {
    override fun enabled(kclass: kotlin.reflect.KClass<out io.kotest.core.spec.Spec>): Boolean =
        isIntegTestEnabled()
}
