package com.glassthought.chainsaw.core.wingman

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class ClaudeCodeWingmanTest : AsgardDescribeSpec({

    describe("GIVEN a ClaudeCodeWingman with a temp projects directory") {
        val guid = "test-guid-abc123-unique-marker"

        describe("AND a single JSONL file containing the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN returns the session ID extracted from the filename") {
                    val tempDir = createTempDirectory("wingman-test-")
                    try {
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
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }
            }
        }

        describe("AND no JSONL files contain the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN throws IllegalStateException") {
                    val tempDir = createTempDirectory("wingman-test-")
                    try {
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
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }

                it("THEN exception message contains the GUID") {
                    val tempDir = createTempDirectory("wingman-test-")
                    try {
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
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }
            }
        }

        describe("AND multiple JSONL files contain the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN throws IllegalStateException") {
                    val tempDir = createTempDirectory("wingman-test-")
                    try {
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
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }

                it("THEN exception message mentions ambiguous") {
                    val tempDir = createTempDirectory("wingman-test-")
                    try {
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
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }
            }
        }

        describe("AND JSONL files are in nested subdirectories") {
            describe("WHEN resolveSessionId is called") {
                it("THEN finds the GUID in nested files and returns session ID") {
                    val tempDir = createTempDirectory("wingman-test-")
                    try {
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
                    } finally {
                        tempDir.toFile().deleteRecursively()
                    }
                }
            }
        }
    }
})
