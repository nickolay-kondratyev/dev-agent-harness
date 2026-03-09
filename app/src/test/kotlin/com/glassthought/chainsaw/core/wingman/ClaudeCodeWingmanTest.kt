package com.glassthought.chainsaw.core.wingman

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class ClaudeCodeWingmanTest : AsgardDescribeSpec({

    describe("GIVEN a ClaudeCodeWingman with a temp projects directory") {
        val guid = "test-guid-abc123-unique-marker"

        describe("AND a single JSONL file containing the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN returns the session ID extracted from the filename") {
                    withTempDir { tempDir ->
                        val sessionId = "77d5b7ea-cf04-453b-8867-162404763e18"
                        tempDir.resolve("$sessionId.jsonl").writeText(
                            """{"type":"message","content":"$guid"}"""
                        )

                        val wingman = ClaudeCodeWingman(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        val result = wingman.resolveSessionId(guid)

                        result shouldBe sessionId
                    }
                }
            }
        }

        describe("AND no JSONL files contain the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN throws IllegalStateException") {
                    withTempDir { tempDir ->
                        tempDir.resolve("some-session.jsonl").writeText(
                            """{"type":"message","content":"different-content"}"""
                        )

                        val wingman = ClaudeCodeWingman(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        shouldThrow<IllegalStateException> {
                            wingman.resolveSessionId(guid)
                        }
                    }
                }

                it("THEN exception message contains the GUID") {
                    withTempDir { tempDir ->
                        tempDir.resolve("some-session.jsonl").writeText(
                            """{"type":"message","content":"different-content"}"""
                        )

                        val wingman = ClaudeCodeWingman(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        val exception = shouldThrow<IllegalStateException> {
                            wingman.resolveSessionId(guid)
                        }
                        exception.message shouldContain guid
                    }
                }
            }
        }

        describe("AND multiple JSONL files contain the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN throws IllegalStateException") {
                    withTempDir { tempDir ->
                        tempDir.resolve("session-a.jsonl").writeText(
                            """{"content":"$guid"}"""
                        )
                        tempDir.resolve("session-b.jsonl").writeText(
                            """{"content":"$guid"}"""
                        )

                        val wingman = ClaudeCodeWingman(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        shouldThrow<IllegalStateException> {
                            wingman.resolveSessionId(guid)
                        }
                    }
                }

                it("THEN exception message mentions ambiguous") {
                    withTempDir { tempDir ->
                        tempDir.resolve("session-a.jsonl").writeText(
                            """{"content":"$guid"}"""
                        )
                        tempDir.resolve("session-b.jsonl").writeText(
                            """{"content":"$guid"}"""
                        )

                        val wingman = ClaudeCodeWingman(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        val exception = shouldThrow<IllegalStateException> {
                            wingman.resolveSessionId(guid)
                        }
                        exception.message shouldContain "Ambiguous"
                    }
                }
            }
        }

        describe("AND JSONL files are in nested subdirectories") {
            describe("WHEN resolveSessionId is called") {
                it("THEN finds the GUID in nested files and returns session ID") {
                    withTempDir { tempDir ->
                        val nestedDir = tempDir.resolve("project-a/sub-dir")
                        nestedDir.createDirectories()

                        val sessionId = "nested-session-id-42"
                        nestedDir.resolve("$sessionId.jsonl").writeText(
                            """{"content":"$guid"}"""
                        )

                        val wingman = ClaudeCodeWingman(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        val result = wingman.resolveSessionId(guid)

                        result shouldBe sessionId
                    }
                }
            }
        }

        describe("AND only non-JSONL files contain the GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN throws IllegalStateException because non-JSONL files are ignored") {
                    withTempDir { tempDir ->
                        tempDir.resolve("notes.txt").writeText(
                            """This file contains $guid but is not a JSONL file"""
                        )
                        tempDir.resolve("data.log").writeText(
                            """{"content":"$guid"}"""
                        )

                        val wingman = ClaudeCodeWingman(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        shouldThrow<IllegalStateException> {
                            wingman.resolveSessionId(guid)
                        }
                    }
                }
            }
        }
    }
})

private suspend fun withTempDir(block: suspend (Path) -> Unit) {
    val tempDir = createTempDirectory("wingman-test-")
    try {
        block(tempDir)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
