package com.glassthought.shepherd.integtest

import com.glassthought.bucket.isIntegTestEnabled
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty

/**
 * Integration test for [ClaudeCodeAdapter] spawn flow using GLM (Z.AI).
 *
 * Spawns a real Claude Code agent session in a tmux pane, redirected to GLM via
 * the [com.glassthought.shepherd.core.agent.adapter.GlmConfig] wired through
 * [SharedContextIntegFactory]. Validates:
 *
 * 1. Tmux session starts successfully and remains alive (agent is interactive, not `-p` mode)
 * 2. Session ID resolution finds the correct JSONL file containing the handshake GUID
 * 3. The tmux session can receive send-keys input (agent is responsive)
 *
 * Command-string construction is covered by unit tests in `ClaudeCodeAdapterTest`.
 *
 * Requires: tmux installed, `Z_AI_GLM_API_TOKEN` secret available, `-PrunIntegTests=true`.
 */
@OptIn(ExperimentalKotest::class)
class ClaudeCodeAdapterSpawnIntegTest : SharedContextDescribeSpec({

    describe("GIVEN ClaudeCodeAdapter with GLM config spawning a real agent").config(isIntegTestEnabled()) {
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val adapter = shepherdContext.infra.claudeCode.agentTypeAdapter
        val createdSessions = mutableListOf<TmuxSession>()

        afterEach {
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed.
                }
            }
            createdSessions.clear()
        }

        val handshakeGuid = HandshakeGuid.generate()
        val sessionName = "integ-claude-spawn-${System.currentTimeMillis()}"

        val params = BuildStartCommandParams(
            bootstrapMessage = "You are a test agent. Your handshake GUID is ${handshakeGuid.value}. " +
                "Respond with exactly: HANDSHAKE_ACK",
            handshakeGuid = handshakeGuid,
            workingDir = System.getProperty("user.dir"),
            model = "sonnet",
            tools = listOf("Bash", "Read", "Write", "Edit"),
            systemPromptFilePath = null,
            appendSystemPrompt = false,
        )

        require(adapter is ClaudeCodeAdapter) {
            "Expected ClaudeCodeAdapter but got ${adapter::class.simpleName}. " +
                "Check SharedContextIntegFactory wiring."
        }

        val startCommand = adapter.buildStartCommand(params)

        describe("WHEN the tmux session is created with the built start command") {

            it("THEN the tmux session exists and is alive") {
                val session = sessionManager.createSession(sessionName, startCommand)
                createdSessions.add(session)

                session.exists() shouldBe true
            }
        }

        describe("WHEN resolveSessionId is called after agent startup") {

            it("THEN session ID is resolved from JSONL file containing the GUID") {
                val freshGuid = HandshakeGuid.generate()
                val freshSessionName = "integ-resolve-${System.currentTimeMillis()}"
                val freshParams = params.copy(
                    bootstrapMessage = "You are a test agent. Your handshake GUID is ${freshGuid.value}. " +
                        "Respond with exactly: HANDSHAKE_ACK",
                    handshakeGuid = freshGuid,
                )
                val freshCommand = adapter.buildStartCommand(freshParams)
                val session = sessionManager.createSession(freshSessionName, freshCommand)
                createdSessions.add(session)

                // resolveSessionId polls until the GUID appears in a JSONL file (45s timeout).
                // Claude Code writes the JSONL file shortly after session startup.
                val sessionId = adapter.resolveSessionId(freshGuid)

                sessionId.shouldNotBeEmpty()
            }
        }

        describe("WHEN send-keys is called on a live agent session") {

            it("THEN the session accepts the input without error") {
                val freshGuid = HandshakeGuid.generate()
                val freshSessionName = "integ-sendkeys-${System.currentTimeMillis()}"
                val freshParams = params.copy(
                    bootstrapMessage = "You are a test agent. Your handshake GUID is ${freshGuid.value}. " +
                        "Respond with exactly: HANDSHAKE_ACK",
                    handshakeGuid = freshGuid,
                )
                val freshCommand = adapter.buildStartCommand(freshParams)
                val session = sessionManager.createSession(freshSessionName, freshCommand)
                createdSessions.add(session)

                // Wait for the agent to initialize by resolving its session ID first.
                adapter.resolveSessionId(freshGuid)

                // Verify the session can receive send-keys input.
                // If the session is not interactive (e.g., `-p` mode), this would fail.
                session.sendKeys("echo test-send-keys-verification")

                // Session should still be alive after receiving input.
                session.exists() shouldBe true
            }
        }
    }
})
