package com.glassthought.shepherd.core.agent.adapter

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class ClaudeCodeAdapterTest : AsgardDescribeSpec({

    val testGuid = HandshakeGuid("handshake.test-uuid-1234")
    val bootstrapMessage = "Your GUID is handshake.test-uuid-1234. Call callback_shepherd.signal.sh started"

    // -- buildStartCommand tests --

    describe("GIVEN ClaudeCodeAdapter") {

        describe("AND all flags enabled") {
            val adapter = ClaudeCodeAdapter.create(
                claudeProjectsDir = Path.of("/dev/null"),
                outFactory = outFactory,
            )

            val params = BuildStartCommandParams(
                bootstrapMessage = bootstrapMessage,
                handshakeGuid = testGuid,
                workingDir = "/home/user/project",
                model = "sonnet",
                tools = listOf("Read", "Write"),
                systemPromptFilePath = "/path/to/prompt.txt",
                appendSystemPrompt = false,
            )

            describe("WHEN buildStartCommand is called") {
                val command = adapter.buildStartCommand(params).command

                it("THEN command contains --model sonnet") {
                    command shouldContain "--model sonnet"
                }

                it("THEN command contains --tools Read,Write") {
                    command shouldContain "--tools Read,Write"
                }

                it("THEN command contains --system-prompt-file with the file path") {
                    command shouldContain "--system-prompt-file /path/to/prompt.txt"
                }

                it("THEN command always contains --dangerously-skip-permissions (Docker invariant)") {
                    command shouldContain "--dangerously-skip-permissions"
                }

                it("THEN command starts with bash -c and includes cd to working dir") {
                    command shouldContain "bash -c 'cd /home/user/project && unset CLAUDECODE && export"
                }

                it("THEN command exports TICKET_SHEPHERD_HANDSHAKE_GUID with the handshake guid value") {
                    command shouldContain "export ${Constants.AGENT_COMM.HANDSHAKE_GUID_ENV_VAR}=${testGuid.value}"
                }

                it("THEN command contains the bootstrap message as a positional argument") {
                    command shouldContain bootstrapMessage
                }
            }
        }

        describe("AND appendSystemPrompt=true") {
            val adapter = ClaudeCodeAdapter.create(
                claudeProjectsDir = Path.of("/dev/null"),
                outFactory = outFactory,
            )

            val params = BuildStartCommandParams(
                bootstrapMessage = bootstrapMessage,
                handshakeGuid = testGuid,
                workingDir = "/home/user/project",
                model = "opus",
                tools = listOf("Bash", "Edit", "Read"),
                systemPromptFilePath = "/path/to/append-prompt.txt",
                appendSystemPrompt = true,
            )

            describe("WHEN buildStartCommand is called") {
                val command = adapter.buildStartCommand(params).command

                it("THEN command contains --append-system-prompt-file") {
                    command shouldContain "--append-system-prompt-file /path/to/append-prompt.txt"
                }

                it("THEN command does NOT contain bare --system-prompt-file (without append prefix)") {
                    val withoutAppend = command.replace("--append-system-prompt-file", "")
                    withoutAppend shouldNotContain "--system-prompt-file"
                }
            }
        }

        describe("AND without system prompt file") {
            val adapter = ClaudeCodeAdapter.create(
                claudeProjectsDir = Path.of("/dev/null"),
                outFactory = outFactory,
            )

            val params = BuildStartCommandParams(
                bootstrapMessage = bootstrapMessage,
                handshakeGuid = testGuid,
                workingDir = "/tmp/test",
                model = "sonnet",
                tools = listOf("Read"),
                systemPromptFilePath = null,
                appendSystemPrompt = false,
            )

            describe("WHEN buildStartCommand is called") {
                val command = adapter.buildStartCommand(params).command

                it("THEN command does not contain --system-prompt-file") {
                    command shouldNotContain "--system-prompt-file"
                }

                it("THEN command does not contain --append-system-prompt-file") {
                    command shouldNotContain "--append-system-prompt-file"
                }

                it("THEN command still contains --dangerously-skip-permissions (Docker invariant)") {
                    command shouldContain "--dangerously-skip-permissions"
                }
            }
        }

        describe("AND file path containing single quotes") {
            val adapter = ClaudeCodeAdapter.create(
                claudeProjectsDir = Path.of("/dev/null"),
                outFactory = outFactory,
            )

            val params = BuildStartCommandParams(
                bootstrapMessage = bootstrapMessage,
                handshakeGuid = testGuid,
                workingDir = "/home/user/project",
                model = "sonnet",
                tools = listOf("Read"),
                systemPromptFilePath = "/path/to/it's-a-prompt.txt",
                appendSystemPrompt = false,
            )

            describe("WHEN buildStartCommand is called") {
                val command = adapter.buildStartCommand(params).command

                it("THEN single quotes in the file path are escaped for the bash -c wrapper") {
                    command shouldContain "it'\\''s-a-prompt.txt"
                }

                it("THEN the command is a valid bash -c wrapper") {
                    command.startsWith("bash -c '") shouldBe true
                    command.endsWith("'") shouldBe true
                }
            }
        }

        describe("AND workingDir containing single quote") {
            val adapter = ClaudeCodeAdapter.create(
                claudeProjectsDir = Path.of("/dev/null"),
                outFactory = outFactory,
            )

            val params = BuildStartCommandParams(
                bootstrapMessage = bootstrapMessage,
                handshakeGuid = testGuid,
                workingDir = "/home/user/it's-a-project",
                model = "sonnet",
                tools = listOf("Read"),
                systemPromptFilePath = null,
                appendSystemPrompt = false,
            )

            describe("WHEN buildStartCommand is called") {
                val command = adapter.buildStartCommand(params).command

                it("THEN single quote in workingDir is properly escaped") {
                    command shouldContain "cd /home/user/it'\\''s-a-project"
                }
            }
        }

        describe("AND empty tools") {
            val adapter = ClaudeCodeAdapter.create(
                claudeProjectsDir = Path.of("/dev/null"),
                outFactory = outFactory,
            )

            val params = BuildStartCommandParams(
                bootstrapMessage = bootstrapMessage,
                handshakeGuid = testGuid,
                workingDir = "/tmp/test",
                model = "sonnet",
                tools = emptyList(),
                systemPromptFilePath = null,
                appendSystemPrompt = false,
            )

            describe("WHEN buildStartCommand is called") {
                val command = adapter.buildStartCommand(params).command

                it("THEN command does not contain --tools") {
                    command shouldNotContain "--tools"
                }
            }
        }

        describe("AND bootstrap message containing shell-special characters") {
            val adapter = ClaudeCodeAdapter.create(
                claudeProjectsDir = Path.of("/dev/null"),
                outFactory = outFactory,
            )

            val specialMessage = "GUID is \$HOME and `command` and \"quoted\""

            val params = BuildStartCommandParams(
                bootstrapMessage = specialMessage,
                handshakeGuid = testGuid,
                workingDir = "/tmp/test",
                model = "sonnet",
                tools = emptyList(),
                systemPromptFilePath = null,
                appendSystemPrompt = false,
            )

            describe("WHEN buildStartCommand is called") {
                val command = adapter.buildStartCommand(params).command

                it("THEN bootstrap message dollar signs are escaped") {
                    command shouldContain "\\$"
                }

                it("THEN bootstrap message backticks are escaped") {
                    command shouldContain "\\`"
                }
            }
        }
    }

    // -- GLM config injection tests --

    describe("GIVEN ClaudeCodeAdapter with GlmConfig") {
        val glmConfig = GlmConfig(
            baseUrl = "https://api.z.ai/api/anthropic",
            authToken = "test-token-abc123",
            defaultOpusModel = "glm-5",
            defaultSonnetModel = "glm-5",
            defaultHaikuModel = "glm-4-flash",
        )

        val adapter = ClaudeCodeAdapter(
            guidScanner = GuidScanner { emptyList() },
            outFactory = outFactory,
            glmConfig = glmConfig,
        )

        val params = BuildStartCommandParams(
            bootstrapMessage = bootstrapMessage,
            handshakeGuid = testGuid,
            workingDir = "/home/user/project",
            model = "sonnet",
            tools = listOf("Read"),
            systemPromptFilePath = null,
            appendSystemPrompt = false,
        )

        describe("WHEN buildStartCommand is called") {
            val command = adapter.buildStartCommand(params).command

            it("THEN command contains ANTHROPIC_BASE_URL export") {
                command shouldContain "export ANTHROPIC_BASE_URL=https://api.z.ai/api/anthropic"
            }

            it("THEN command contains ANTHROPIC_AUTH_TOKEN export with the token value") {
                command shouldContain "export ANTHROPIC_AUTH_TOKEN=test-token-abc123"
            }

            it("THEN command contains CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 export") {
                command shouldContain "export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1"
            }

            it("THEN command contains ANTHROPIC_DEFAULT_OPUS_MODEL export") {
                command shouldContain "export ANTHROPIC_DEFAULT_OPUS_MODEL=glm-5"
            }

            it("THEN command contains ANTHROPIC_DEFAULT_SONNET_MODEL export") {
                command shouldContain "export ANTHROPIC_DEFAULT_SONNET_MODEL=glm-5"
            }

            it("THEN command contains ANTHROPIC_DEFAULT_HAIKU_MODEL export") {
                command shouldContain "export ANTHROPIC_DEFAULT_HAIKU_MODEL=glm-4-flash"
            }

            it("THEN GLM exports appear before cd to working directory") {
                val glmIndex = command.indexOf("export ANTHROPIC_BASE_URL")
                val cdIndex = command.indexOf("cd /home/user/project")
                (glmIndex < cdIndex) shouldBe true
            }

            it("THEN command still contains the cd and claude command after GLM exports") {
                command shouldContain "cd /home/user/project && unset CLAUDECODE"
            }
        }
    }

    describe("GIVEN ClaudeCodeAdapter without GlmConfig (null)") {
        val adapter = ClaudeCodeAdapter(
            guidScanner = GuidScanner { emptyList() },
            outFactory = outFactory,
            glmConfig = null,
        )

        val params = BuildStartCommandParams(
            bootstrapMessage = bootstrapMessage,
            handshakeGuid = testGuid,
            workingDir = "/home/user/project",
            model = "sonnet",
            tools = listOf("Read"),
            systemPromptFilePath = null,
            appendSystemPrompt = false,
        )

        describe("WHEN buildStartCommand is called") {
            val command = adapter.buildStartCommand(params).command

            it("THEN command does NOT contain ANTHROPIC_BASE_URL") {
                command shouldNotContain "ANTHROPIC_BASE_URL"
            }

            it("THEN command does NOT contain ANTHROPIC_AUTH_TOKEN") {
                command shouldNotContain "ANTHROPIC_AUTH_TOKEN"
            }

            it("THEN command does NOT contain CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC") {
                command shouldNotContain "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"
            }

            it("THEN command starts with bash -c and cd (no GLM prefix)") {
                command shouldContain "bash -c 'cd /home/user/project && unset CLAUDECODE"
            }
        }
    }

    describe("GIVEN GlmConfig.standard factory") {
        val config = GlmConfig.standard(authToken = "my-secret-token")

        it("THEN baseUrl is the Z.AI endpoint") {
            config.baseUrl shouldBe "https://api.z.ai/api/anthropic"
        }

        it("THEN authToken matches provided token") {
            config.authToken shouldBe "my-secret-token"
        }

        it("THEN defaultOpusModel is glm-5") {
            config.defaultOpusModel shouldBe "glm-5"
        }

        it("THEN defaultSonnetModel is glm-5") {
            config.defaultSonnetModel shouldBe "glm-5"
        }

        it("THEN defaultHaikuModel is glm-4-flash") {
            config.defaultHaikuModel shouldBe "glm-4-flash"
        }
    }

    // -- resolveSessionId tests --

    describe("GIVEN a ClaudeCodeAdapter with a temp projects directory") {
        val guid = HandshakeGuid("test-guid-abc123-unique-marker")

        describe("AND a single JSONL file containing the target GUID") {
            describe("WHEN resolveSessionId is called") {
                it("THEN returns the session ID extracted from the filename") {
                    withTempDir { tempDir ->
                        val sessionId = "77d5b7ea-cf04-453b-8867-162404763e18"
                        tempDir.resolve("$sessionId.jsonl").writeText(
                            """{"type":"message","content":"${guid.value}"}"""
                        )

                        val adapter = ClaudeCodeAdapter.create(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        val result = adapter.resolveSessionId(guid)

                        result shouldBe sessionId
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

                        val adapter = ClaudeCodeAdapter.create(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                            resolveTimeoutMs = 600L,
                        )

                        shouldThrow<IllegalStateException> {
                            adapter.resolveSessionId(guid)
                        }
                    }
                }

                it("THEN exception message contains the GUID") {
                    withTempDir { tempDir ->
                        tempDir.resolve("some-session.jsonl").writeText(
                            """{"type":"message","content":"different-content"}"""
                        )

                        val adapter = ClaudeCodeAdapter.create(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                            resolveTimeoutMs = 600L,
                        )

                        val exception = shouldThrow<IllegalStateException> {
                            adapter.resolveSessionId(guid)
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

                        val adapter = ClaudeCodeAdapter.create(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        shouldThrow<IllegalStateException> {
                            adapter.resolveSessionId(guid)
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

                        val adapter = ClaudeCodeAdapter.create(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        val exception = shouldThrow<IllegalStateException> {
                            adapter.resolveSessionId(guid)
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

                        val adapter = ClaudeCodeAdapter.create(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                        )

                        val result = adapter.resolveSessionId(guid)

                        result shouldBe sessionId
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

                        val adapter = ClaudeCodeAdapter.create(
                            claudeProjectsDir = tempDir,
                            outFactory = outFactory,
                            resolveTimeoutMs = 600L,
                        )

                        shouldThrow<IllegalStateException> {
                            adapter.resolveSessionId(guid)
                        }
                    }
                }
            }
        }
    }

    describe("GIVEN a ClaudeCodeAdapter with a fake GuidScanner") {
        val guid = HandshakeGuid("polling-test-guid")
        val matchPath = Path.of("/fake/sessions/abc-session-id-123.jsonl")

        describe("AND the scanner returns empty on first call then a match on the second call") {
            describe("WHEN resolveSessionId is called") {
                it("THEN returns the session ID from the matched path") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = 1,
                        matchOnSuccess = listOf(matchPath),
                    )

                    val adapter = ClaudeCodeAdapter(
                        guidScanner = fakeScanner,
                        outFactory = outFactory,
                        pollIntervalMs = 1L,
                    )

                    val result = adapter.resolveSessionId(guid)

                    result shouldBe "abc-session-id-123"
                }

                it("THEN polls more than once before finding the match") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = 1,
                        matchOnSuccess = listOf(matchPath),
                    )

                    val adapter = ClaudeCodeAdapter(
                        guidScanner = fakeScanner,
                        outFactory = outFactory,
                        pollIntervalMs = 1L,
                    )

                    adapter.resolveSessionId(guid)

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

                    val adapter = ClaudeCodeAdapter(
                        guidScanner = fakeScanner,
                        outFactory = outFactory,
                        pollIntervalMs = 1L,
                    )

                    adapter.resolveSessionId(guid)

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

                    val adapter = ClaudeCodeAdapter(
                        guidScanner = fakeScanner,
                        outFactory = outFactory,
                        resolveTimeoutMs = 100L,
                        pollIntervalMs = 10L,
                    )

                    shouldThrow<IllegalStateException> {
                        adapter.resolveSessionId(guid)
                    }
                }

                it("THEN exception message contains the GUID") {
                    val fakeScanner = CountingFakeGuidScanner(
                        emptyResultCount = Int.MAX_VALUE,
                        matchOnSuccess = emptyList(),
                    )

                    val adapter = ClaudeCodeAdapter(
                        guidScanner = fakeScanner,
                        outFactory = outFactory,
                        resolveTimeoutMs = 100L,
                        pollIntervalMs = 10L,
                    )

                    val exception = shouldThrow<IllegalStateException> {
                        adapter.resolveSessionId(guid)
                    }
                    exception.message shouldContain guid.value
                }
            }
        }
    }
})

/**
 * Fake [GuidScanner] for testing the polling behavior.
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
    val tempDir = createTempDirectory("claude-adapter-test-")
    try {
        block(tempDir)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
