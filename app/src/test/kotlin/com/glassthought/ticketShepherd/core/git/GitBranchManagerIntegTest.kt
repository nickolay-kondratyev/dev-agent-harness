package com.glassthought.ticketShepherd.core.git

import com.asgard.core.processRunner.ProcessRunner
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.ticketShepherd.core.supporting.git.GitBranchManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import com.glassthought.bucket.isIntegTestEnabled
import java.nio.file.Files

/**
 * Integration tests for [com.glassthought.ticketShepherd.core.supporting.git.GitBranchManager].
 *
 * Requires git to be installed. Gated with [isIntegTestEnabled].
 * Each test creates an isolated temporary git repository.
 */
@OptIn(ExperimentalKotest::class)
class GitBranchManagerIntegTest : AsgardDescribeSpec({

    describe("GIVEN a fresh git repo").config(isIntegTestEnabled()) {
        val processRunner = ProcessRunner.standard(outFactory)

        describe("WHEN getCurrentBranch is called") {
            it("THEN returns 'main'") {
                val tempDir = Files.createTempDirectory("git-branch-mgr-test")
                try {
                    initGitRepo(processRunner, tempDir.toString())

                    val manager = GitBranchManager.standard(outFactory, processRunner, tempDir)
                    val branch = manager.getCurrentBranch()

                    branch shouldBe "main"
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }

        describe("WHEN createAndCheckout is called with 'feature__test__try-1'") {
            it("THEN getCurrentBranch returns 'feature__test__try-1'") {
                val tempDir = Files.createTempDirectory("git-branch-mgr-test")
                try {
                    initGitRepo(processRunner, tempDir.toString())

                    val manager = GitBranchManager.standard(outFactory, processRunner, tempDir)
                    manager.createAndCheckout("feature__test__try-1")
                    val branch = manager.getCurrentBranch()

                    branch shouldBe "feature__test__try-1"
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }

        describe("WHEN createAndCheckout is called with an already-existing branch name") {
            it("THEN throws RuntimeException") {
                val tempDir = Files.createTempDirectory("git-branch-mgr-test")
                try {
                    initGitRepo(processRunner, tempDir.toString())

                    val manager = GitBranchManager.standard(outFactory, processRunner, tempDir)
                    manager.createAndCheckout("duplicate-branch")

                    shouldThrow<RuntimeException> {
                        manager.createAndCheckout("duplicate-branch")
                    }
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }

        describe("WHEN createAndCheckout is called with a blank name") {
            it("THEN throws IllegalArgumentException") {
                val tempDir = Files.createTempDirectory("git-branch-mgr-test")
                try {
                    initGitRepo(processRunner, tempDir.toString())

                    val manager = GitBranchManager.standard(outFactory, processRunner, tempDir)

                    shouldThrow<IllegalArgumentException> {
                        manager.createAndCheckout("  ")
                    }
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }
    }
})

/**
 * Initializes a git repository in [dir] with:
 * - `git init`
 * - explicit `git checkout -b main` (avoids dependence on system init.defaultBranch config)
 * - local user.email and user.name config
 * - an initial empty commit
 */
private suspend fun initGitRepo(processRunner: ProcessRunner, dir: String) {
    processRunner.runProcess("git", "-C", dir, "init")
    processRunner.runProcess("git", "-C", dir, "checkout", "-b", "main")
    processRunner.runProcess("git", "-C", dir, "config", "user.email", "test@test.com")
    processRunner.runProcess("git", "-C", dir, "config", "user.name", "Test")
    processRunner.runProcess("git", "-C", dir, "commit", "--allow-empty", "-m", "initial")
}
