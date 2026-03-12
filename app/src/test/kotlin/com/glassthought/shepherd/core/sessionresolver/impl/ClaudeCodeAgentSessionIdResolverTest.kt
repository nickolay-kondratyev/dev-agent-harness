package com.glassthought.shepherd.core.sessionresolver.impl

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.agent.sessionresolver.impl.ClaudeCodeAgentSessionIdResolver
import com.glassthought.shepherd.core.agent.sessionresolver.impl.GuidScanner
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

private const val TEST_MODEL = "test-model-sonnet"

class ClaudeCodeAgentSessionIdResolverTest : AsgardDescribeSpec({

    describe("GIVEN a ClaudeCodeAgentSessionIdResolver with a temp projects directory") {
        val guid = HandshakeGuid("test-guid-abc123-unique-marker")

        describe("AND a single JSONL file containing the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN returns the session ID extracted from the filename") {
                    withTempDir { tempDir ->
                        val sessionId = "77d5b7ea-cf04-453b-8867-162404763e18"
                        tempDir.resolve("$sessionId.jsonl").writeText(
                            """{"type":"message","content":"${guid.value}"}"""
                        )

                        val resolver = ClaudeCodeAgentSessionIdResolver(
                          claudeProjectsDir = tempDir,
                          outFactory = outFactory,
                        )

                        val result = resolver.resolveSessionId(guid, TEST_MODEL)

                        result shouldBe ResumableAgentSessionId(guid, AgentType.CLAUDE_CODE, sessionId, TEST_MODEL)
                    }
                }
            }
        }

        describe("AND no JSONL files contain the target GUID") {
            describe("WHEN resolveSessionId is called with a short timeout") {
                it("THEN throws IllegalStateException") {
                    withTempDir { tempDir ->
                        tempDir.resolve("some-session.jsonl").writeText(
                            """{"type":"message","content":"different-content"}"""
                        )

                        // Short timeout so the test is fast; real timeout is 45 seconds.
                        val resolver = ClaudeCodeAgentSessionIdResolver(
                          claudeProjectsDir = tempDir,
                          outFactory = outFactory,
                          resolveTimeoutMs = 600L,
                        )

                        shouldThrow<IllegalStateException> {
                            resolver.resolveSessionId(guid, TEST_MODEL)
                        }
                    }
                }

                it("THEN exception message contains the GUID") {
                    withTempDir { tempDir ->
                        tempDir.resolve("some-session.jsonl").writeText(
                            """{"type":"message","content":"different-content"}"""
                        )

                        val resolver = ClaudeCodeAgentSessionIdResolver(
                          claudeProjectsDir = tempDir,
                          outFactory = outFactory,
                          resolveTimeoutMs = 600L,
                        )

                        val exception = shouldThrow<IllegalStateException> {
                            resolver.resolveSessionId(guid, TEST_MODEL)
                        }
                        exception.message shouldContain guid.value
                    }
                }
            }
        }

        describe("AND multiple JSONL files contain the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN throws IllegalStateException") {
                    withTempDir { tempDir ->
                        tempDir.resolve("session-a.jsonl").writeText(
                            """{"content":"${guid.value}"}"""
                        )
                        tempDir.resolve("session-b.jsonl").writeText(
                            """{"content":"${guid.value}"}"""
                        )

                        val resolver = ClaudeCodeAgentSessionIdResolver(
                          claudeProjectsDir = tempDir,
                          outFactory = outFactory,
                        )

                        shouldThrow<IllegalStateException> {
                            resolver.resolveSessionId(guid, TEST_MODEL)
                        }
                    }
                }

                it("THEN exception message mentions ambiguous") {
                    withTempDir { tempDir ->
                        tempDir.resolve("session-a.jsonl").writeText(
                            """{"content":"${guid.value}"}"""
                        )
                        tempDir.resolve("session-b.jsonl").writeText(
                            """{"content":"${guid.value}"}"""
                        )

                        val resolver = ClaudeCodeAgentSessionIdResolver(
                          claudeProjectsDir = tempDir,
                          outFactory = outFactory,
                        )

                        val exception = shouldThrow<IllegalStateException> {
                            resolver.resolveSessionId(guid, TEST_MODEL)
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
                            """{"content":"${guid.value}"}"""
                        )

                        val resolver = ClaudeCodeAgentSessionIdResolver(
                          claudeProjectsDir = tempDir,
                          outFactory = outFactory,
                        )

                        val result = resolver.resolveSessionId(guid, TEST_MODEL)

                        result shouldBe ResumableAgentSessionId(guid, AgentType.CLAUDE_CODE, sessionId, TEST_MODEL)
                    }
                }
            }
        }

        describe("AND only non-JSONL files contain the GUID") {
            describe("WHEN resolveSessionId is called with a short timeout") {
                it("THEN throws IllegalStateException because non-JSONL files are ignored") {
                    withTempDir { tempDir ->
                        tempDir.resolve("notes.txt").writeText(
                            """This file contains ${guid.value} but is not a JSONL file"""
                        )
                        tempDir.resolve("data.log").writeText(
                            """{"content":"${guid.value}"}"""
                        )

                        val resolver = ClaudeCodeAgentSessionIdResolver(
                          claudeProjectsDir = tempDir,
                          outFactory = outFactory,
                          resolveTimeoutMs = 600L,
                        )

                        shouldThrow<IllegalStateException> {
                            resolver.resolveSessionId(guid, TEST_MODEL)
                        }
                    }
                }
            }
        }
    }

    describe("GIVEN a ClaudeCodeAgentSessionIdResolver with a fake GuidScanner") {
        val guid = HandshakeGuid("polling-test-guid")
        val matchPath = Path.of("/fake/sessions/abc-session-id-123.jsonl")

        describe("AND the scanner returns empty on first call then a match on the second call") {
            describe("WHEN resolveSessionId is called") {
                it("THEN returns the session ID from the matched path") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = 1,
                        matchOnSuccess = listOf(matchPath),
                    )

                    val resolver = ClaudeCodeAgentSessionIdResolver(
                      guidScanner = fakeScanner,
                      outFactory = outFactory,
                      pollIntervalMs = 1L,
                    )

                    val result = resolver.resolveSessionId(guid, TEST_MODEL)

                    result shouldBe ResumableAgentSessionId(guid, AgentType.CLAUDE_CODE, "abc-session-id-123", TEST_MODEL)
                }

                it("THEN polls more than once before finding the match") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = 1,
                        matchOnSuccess = listOf(matchPath),
                    )

                    val resolver = ClaudeCodeAgentSessionIdResolver(
                      guidScanner = fakeScanner,
                      outFactory = outFactory,
                      pollIntervalMs = 1L,
                    )

                    resolver.resolveSessionId(guid, TEST_MODEL)

                    fakeScanner.callCount shouldBe 2
                }
            }
        }

        describe("AND the scanner returns empty on the first 3 calls then a match") {
            describe("WHEN resolveSessionId is called") {
                it("THEN returns the session ID after 4 total poll attempts") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = 3,
                        matchOnSuccess = listOf(matchPath),
                    )

                    val resolver = ClaudeCodeAgentSessionIdResolver(
                      guidScanner = fakeScanner,
                      outFactory = outFactory,
                      pollIntervalMs = 1L,
                    )

                    resolver.resolveSessionId(guid, TEST_MODEL)

                    fakeScanner.callCount shouldBe 4
                }
            }
        }

        describe("AND the scanner always returns empty and timeout is very short") {
            describe("WHEN resolveSessionId is called") {
                it("THEN throws IllegalStateException wrapping the timeout") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = Int.MAX_VALUE,
                        matchOnSuccess = emptyList(),
                    )

                    val resolver = ClaudeCodeAgentSessionIdResolver(
                      guidScanner = fakeScanner,
                      outFactory = outFactory,
                      resolveTimeoutMs = 100L,
                      pollIntervalMs = 10L,
                    )

                    shouldThrow<IllegalStateException> {
                        resolver.resolveSessionId(guid, TEST_MODEL)
                    }
                }

                it("THEN exception message contains the GUID") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = Int.MAX_VALUE,
                        matchOnSuccess = emptyList(),
                    )

                    val resolver = ClaudeCodeAgentSessionIdResolver(
                      guidScanner = fakeScanner,
                      outFactory = outFactory,
                      resolveTimeoutMs = 100L,
                      pollIntervalMs = 10L,
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        resolver.resolveSessionId(guid, TEST_MODEL)
                    }
                    exception.message shouldContain guid.value
                }
            }
        }
    }
})

/**
 * Fake [com.glassthought.shepherd.core.agent.sessionresolver.impl.GuidScanner] for testing the polling behavior.
 *
 * Returns an empty list for the first [emptyResultCount] calls, then returns [matchOnSuccess].
 * Tracks [callCount] for assertion.
 */
private class CountingFakeGuidScanner(
    private val emptyResultCount: Int,
    private val matchOnSuccess: List<Path>,
) : GuidScanner {

    var callCount: Int = 0
        private set

    override suspend fun scan(guid: HandshakeGuid): List<Path> {
        callCount++
        return if (callCount <= emptyResultCount) emptyList() else matchOnSuccess
    }
}

private suspend fun withTempDir(block: suspend (Path) -> Unit) {
    val tempDir = createTempDirectory("session-resolver-test-")
    try {
        block(tempDir)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
