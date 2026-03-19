package com.glassthought.shepherd.core.agent.contextwindow

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.time.TestClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

class ClaudeCodeContextWindowStateReaderTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

    val sessionId = "test-session-42"
    val freshTimestamp = Instant.parse("2026-03-19T10:00:00Z")

    fun buildReader(
        basePath: Path,
        clock: TestClock = TestClock(freshTimestamp),
        config: HarnessTimeoutConfig = HarnessTimeoutConfig.forTests(),
    ): ClaudeCodeContextWindowStateReader {
        return ClaudeCodeContextWindowStateReader(
            basePath = basePath,
            clock = clock,
            harnessTimeoutConfig = config,
            outFactory = outFactory,
        )
    }

    fun writeJsonFile(basePath: Path, sessionId: String, content: String): Path {
        val dir = basePath.resolve(sessionId)
        Files.createDirectories(dir)
        val file = dir.resolve("context_window_slim.json")
        Files.writeString(file, content)
        return file
    }

    describe("GIVEN a valid JSON file with fresh timestamp") {
        val basePath = Files.createTempDirectory("ctx-reader-test")
        val clock = TestClock(freshTimestamp)
        val reader = buildReader(basePath, clock)

        writeJsonFile(
            basePath, sessionId,
            """{"file_updated_timestamp": "2026-03-19T10:00:00Z", "remaining_percentage": 72}"""
        )

        describe("WHEN read is called") {
            it("THEN returns ContextWindowState with remainingPercentage") {
                val result = reader.read(sessionId)
                result.remainingPercentage shouldBe 72
            }
        }
    }

    describe("GIVEN a valid JSON file with stale timestamp") {
        val basePath = Files.createTempDirectory("ctx-reader-stale-test")
        // Clock is 10 minutes ahead of file timestamp; stale timeout is 2 seconds (forTests)
        val clock = TestClock(freshTimestamp.plusSeconds(600))
        val reader = buildReader(basePath, clock)

        writeJsonFile(
            basePath, sessionId,
            """{"file_updated_timestamp": "2026-03-19T10:00:00Z", "remaining_percentage": 72}"""
        )

        describe("WHEN read is called") {
            it("THEN returns ContextWindowState with remainingPercentage = null").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN))
            ) {
                val result = reader.read(sessionId)
                result.remainingPercentage shouldBe null
            }
        }
    }

    describe("GIVEN the JSON file does not exist") {
        val basePath = Files.createTempDirectory("ctx-reader-missing-test")
        val reader = buildReader(basePath)

        describe("WHEN read is called") {
            it("THEN throws ContextWindowStateUnavailableException") {
                val exception = shouldThrow<ContextWindowStateUnavailableException> {
                    reader.read(sessionId)
                }
                exception.message shouldContain "not found"
            }
        }
    }

    describe("GIVEN a malformed JSON file with missing fields") {
        val basePath = Files.createTempDirectory("ctx-reader-missing-fields-test")
        val reader = buildReader(basePath)

        writeJsonFile(
            basePath, sessionId,
            """{"file_updated_timestamp": "2026-03-19T10:00:00Z"}"""
        )

        describe("WHEN read is called") {
            it("THEN throws ContextWindowStateUnavailableException") {
                val exception = shouldThrow<ContextWindowStateUnavailableException> {
                    reader.read(sessionId)
                }
                exception.message shouldContain "Failed to parse"
            }
        }
    }

    describe("GIVEN an unparseable JSON file") {
        val basePath = Files.createTempDirectory("ctx-reader-invalid-json-test")
        val reader = buildReader(basePath)

        writeJsonFile(
            basePath, sessionId,
            """not valid json at all"""
        )

        describe("WHEN read is called") {
            it("THEN throws ContextWindowStateUnavailableException") {
                val exception = shouldThrow<ContextWindowStateUnavailableException> {
                    reader.read(sessionId)
                }
                exception.message shouldContain "Failed to parse"
            }
        }
    }

    describe("GIVEN a JSON file with invalid timestamp format") {
        val basePath = Files.createTempDirectory("ctx-reader-bad-timestamp-test")
        val reader = buildReader(basePath)

        writeJsonFile(
            basePath, sessionId,
            """{"file_updated_timestamp": "not-a-timestamp", "remaining_percentage": 50}"""
        )

        describe("WHEN read is called") {
            it("THEN throws ContextWindowStateUnavailableException") {
                val exception = shouldThrow<ContextWindowStateUnavailableException> {
                    reader.read(sessionId)
                }
                exception.message shouldContain "Invalid file_updated_timestamp"
            }
        }
    }

    describe("GIVEN a JSON file with remaining_percentage outside valid range") {
        val basePath = Files.createTempDirectory("ctx-reader-bounds-test")
        val reader = buildReader(basePath)

        writeJsonFile(
            basePath, sessionId,
            """{"file_updated_timestamp": "2026-03-19T10:00:00Z", "remaining_percentage": 150}"""
        )

        describe("WHEN read is called") {
            it("THEN throws ContextWindowStateUnavailableException") {
                val exception = shouldThrow<ContextWindowStateUnavailableException> {
                    reader.read(sessionId)
                }
                exception.message shouldContain "outside valid range"
            }
        }
    }

    describe("GIVEN a timestamp exactly at the stale boundary") {
        val basePath = Files.createTempDirectory("ctx-reader-boundary-test")
        val config = HarnessTimeoutConfig(contextFileStaleTimeout = 5.minutes)
        // Clock is exactly 5 minutes after file timestamp — right at boundary
        val clock = TestClock(freshTimestamp.plusSeconds(300))
        val reader = buildReader(basePath, clock, config)

        writeJsonFile(
            basePath, sessionId,
            """{"file_updated_timestamp": "2026-03-19T10:00:00Z", "remaining_percentage": 40}"""
        )

        describe("WHEN read is called") {
            it("THEN returns ContextWindowState with remainingPercentage (not stale)") {
                // At exact boundary: file timestamp == staleThreshold, isBefore is false
                val result = reader.read(sessionId)
                result.remainingPercentage shouldBe 40
            }
        }
    }
})
