package com.glassthought.shepherd.core.supporting.git

import com.asgard.core.out.LogLevel
import com.asgard.core.processRunner.ProcessRunner
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain

// ── Test Fake ───────────────────────────────────────────────────────────────

/**
 * A [ProcessRunner] fake that returns a configured response for `git status --porcelain`.
 */
private class FakeGitStatusProcessRunner(
    private val porcelainOutput: String = "",
) : ProcessRunner {

    override suspend fun runProcess(vararg input: String?): String {
        val key = input.filterNotNull().joinToString(" ")
        if (key == "git status --porcelain") {
            return porcelainOutput
        }
        error("Fake: unrecognized command [$key]")
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

// ── Tests ───────────────────────────────────────────────────────────────────

class WorkingTreeValidatorTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN clean working tree (empty porcelain output)") {
            describe("WHEN validate is called") {
                it("THEN succeeds without throwing") {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = ""),
                    )

                    shouldNotThrowAny {
                        validator.validate()
                    }
                }
            }
        }

        describe("GIVEN clean working tree (whitespace-only porcelain output)") {
            describe("WHEN validate is called") {
                it("THEN succeeds without throwing") {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = "  \n  "),
                    )

                    shouldNotThrowAny {
                        validator.validate()
                    }
                }
            }
        }

        describe("GIVEN dirty working tree with modified files") {
            val dirtyOutput = " M src/Main.kt"

            describe("WHEN validate is called") {
                it("THEN throws IllegalStateException").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = dirtyOutput),
                    )

                    shouldThrow<IllegalStateException> {
                        validator.validate()
                    }
                }

                it("THEN error message contains the dirty file name").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = dirtyOutput),
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        validator.validate()
                    }

                    exception.message shouldContain "src/Main.kt"
                }
            }
        }

        describe("GIVEN dirty working tree with untracked files") {
            val dirtyOutput = "?? newfile.txt"

            describe("WHEN validate is called") {
                it("THEN throws IllegalStateException").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = dirtyOutput),
                    )

                    shouldThrow<IllegalStateException> {
                        validator.validate()
                    }
                }

                it("THEN error message contains the untracked file name").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = dirtyOutput),
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        validator.validate()
                    }

                    exception.message shouldContain "newfile.txt"
                }
            }
        }

        describe("GIVEN dirty working tree with mixed changes") {
            val dirtyOutput = " M src/Main.kt\n?? newfile.txt"

            describe("WHEN validate is called") {
                it("THEN error message contains instruction to commit or stash").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = dirtyOutput),
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        validator.validate()
                    }

                    exception.message shouldContain "Please commit or stash your changes before running 'shepherd run'."
                }

                it("THEN error message contains 'Working tree is not clean'").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = dirtyOutput),
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        validator.validate()
                    }

                    exception.message shouldContain "Working tree is not clean"
                }

                it("THEN error message lists all dirty files").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val validator = WorkingTreeValidator.standard(
                        outFactory = outFactory,
                        processRunner = FakeGitStatusProcessRunner(porcelainOutput = dirtyOutput),
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        validator.validate()
                    }

                    exception.message shouldContain "src/Main.kt"
                    exception.message shouldContain "newfile.txt"
                }
            }
        }
    },
)
