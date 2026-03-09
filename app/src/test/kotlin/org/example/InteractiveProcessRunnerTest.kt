package org.example

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.processRunner.InteractiveProcessResult
import com.glassthought.chainsaw.core.processRunner.InteractiveProcessRunner
import io.kotest.matchers.shouldBe

/**
 * Tests for [InteractiveProcessRunner].
 *
 * NOTE: True interactive tests (requiring a TTY) cannot run in CI.
 * These tests cover construction and non-interactive commands, which work
 * fine with inheritIO even when stdin is not a terminal.
 */
class InteractiveProcessRunnerTest : AsgardDescribeSpec({

    describe("GIVEN InteractiveProcessRunner") {
        it("WHEN constructed THEN no error") {
            InteractiveProcessRunner(outFactory)
        }

        describe("AND a runner instance") {
            val runner = InteractiveProcessRunner(outFactory)

            describe("WHEN runInteractive is called with non-interactive echo") {
                it("THEN exit code is 0") {
                    val result = runner.runInteractive("echo", "hello")
                    result.exitCode shouldBe 0
                }

                it("THEN interrupted is false") {
                    val result = runner.runInteractive("echo", "hello")
                    result.interrupted shouldBe false
                }
            }

            describe("WHEN runInteractive is called with a command that exits non-zero") {
                // `false` is a Unix command that always exits with code 1.
                it("THEN exit code is 1") {
                    val result = runner.runInteractive("false")
                    result.exitCode shouldBe 1
                }
            }
        }
    }

    describe("GIVEN InteractiveProcessResult") {
        describe("WHEN constructed with exitCode 42") {
            val result = InteractiveProcessResult(exitCode = 42, interrupted = false)

            it("THEN exitCode is 42") {
                result.exitCode shouldBe 42
            }
        }

        describe("WHEN constructed with interrupted=true") {
            val result = InteractiveProcessResult(exitCode = -1, interrupted = true)

            it("THEN interrupted is true") {
                result.interrupted shouldBe true
            }
        }
    }
})
