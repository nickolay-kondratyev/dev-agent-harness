package com.glassthought.shepherd.integtest

import com.glassthought.bucket.isIntegTestEnabled
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.Constants
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
 * 1. Start command construction produces a working interactive session
 * 2. Claude Code creates a JSONL session file containing the handshake GUID
 * 3. Session ID resolution finds the correct JSONL file and extracts session ID
 * 4. The tmux session remains alive (agent is interactive)
 *
 * **Resource efficiency**: A single agent session is spawned and reused for all assertions,
 * per the design doc guidance (ref.ap.hhP3gT9qK2mR8vNwX5dYa.E).
 *
 * Requires: tmux installed, `Z_AI_GLM_API_TOKEN` secret available, `-PrunIntegTests=true`.
 */
@OptIn(ExperimentalKotest::class)
class ClaudeCodeAdapterSpawnIntegTest : SharedContextDescribeSpec({

    describe("GIVEN ClaudeCodeAdapter with GLM config spawning a real agent").config(isIntegTestEnabled()) {
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val adapter = shepherdContext.infra.claudeCode.agentTypeAdapter as ClaudeCodeAdapter
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
                // Create a fresh session for this test to avoid timing issues with shared state
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

        describe("WHEN the start command is inspected") {
            val commandStr = startCommand.command

            it("THEN the command contains GLM ANTHROPIC_BASE_URL export") {
                commandStr.contains("export ANTHROPIC_BASE_URL=") shouldBe true
            }

            it("THEN the command contains ANTHROPIC_AUTH_TOKEN export") {
                commandStr.contains("export ANTHROPIC_AUTH_TOKEN=") shouldBe true
            }

            it("THEN the command contains CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC export") {
                commandStr.contains("export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1") shouldBe true
            }

            it("THEN the command contains the handshake GUID env var export") {
                commandStr.contains(
                    "export ${Constants.AGENT_COMM.HANDSHAKE_GUID_ENV_VAR}=${handshakeGuid.value}"
                ) shouldBe true
            }

            it("THEN the command contains --dangerously-skip-permissions") {
                commandStr.contains("--dangerously-skip-permissions") shouldBe true
            }

            it("THEN the command contains --model sonnet") {
                commandStr.contains("--model sonnet") shouldBe true
            }
        }
    }
})
