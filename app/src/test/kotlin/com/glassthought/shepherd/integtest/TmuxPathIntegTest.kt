package com.glassthought.shepherd.integtest

import com.asgard.testTools.awaitility.AsgardAwaitility
import com.glassthought.bucket.isIntegTestEnabled
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.adapter.CallbackScriptsDir
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.adapter.GuidScanner
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integration test that verifies the PATH environment variable is correctly set
 * inside a tmux session when using the command built by [ClaudeCodeAdapter.buildStartCommand].
 *
 * This bridges the gap between:
 * - Unit tests (which verify the command *string* contains `export PATH=...`)
 * - Heavy integration tests (which require a real LLM agent)
 *
 * The approach: build the real command via [ClaudeCodeAdapter], replace the `claude --model ...`
 * portion with `echo $PATH > <tmpFile>`, start a tmux session with the modified command,
 * and verify the captured PATH contains the callback scripts directory.
 *
 * Requires: tmux installed. Does NOT require Claude, GLM, or any LLM.
 */
@OptIn(ExperimentalKotest::class)
class TmuxPathIntegTest : SharedContextDescribeSpec({

    describe("GIVEN a tmux command built by ClaudeCodeAdapter with real CallbackScriptsDir")
        .config(isIntegTestEnabled()) {

        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val callbackScriptsDir: CallbackScriptsDir = IntegTestHelpers.resolveCallbackScriptsDir()

        val adapter = ClaudeCodeAdapter(
            guidScanner = GuidScanner { emptyList() },
            outFactory = shepherdContext.infra.outFactory,
            serverPort = 99999,
            callbackScriptsDir = callbackScriptsDir,
        )

        val dummyParams = BuildStartCommandParams(
            bootstrapMessage = "path-test-bootstrap",
            handshakeGuid = HandshakeGuid("handshake.path-test-guid"),
            workingDir = System.getProperty("user.dir"),
            model = "sonnet",
            tools = emptyList(),
            systemPromptFilePath = null,
            appendSystemPrompt = false,
        )

        val createdSessions = mutableListOf<TmuxSession>()
        val createdFiles = mutableListOf<File>()

        afterEach {
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be dead (command exited).
                }
            }
            createdSessions.clear()
            createdFiles.forEach { file -> file.delete() }
            createdFiles.clear()
        }

        describe("WHEN the command is executed in a tmux session with claude replaced by echo PATH") {

            it("THEN the captured PATH contains the callback scripts directory") {
                val outputFile = File(
                    System.getProperty("user.dir"),
                    ".tmp/path-integ-test-output-${System.currentTimeMillis()}",
                )
                outputFile.parentFile.mkdirs()
                createdFiles.add(outputFile)

                val originalCommand = adapter.buildStartCommand(dummyParams).command

                // Replace the `claude --model ...` portion (up to the closing single quote)
                // with `echo $PATH > <tmpFile>`. The command structure is:
                //   bash -c '...export PATH=$PATH:<dir> && claude --model ...'
                // Inside single quotes, $PATH is literal and expanded by the inner bash.
                val claudeMarker = "claude --model"
                val markerIndex = originalCommand.indexOf(claudeMarker)
                require(markerIndex > 0) {
                    "Expected [$claudeMarker] in command but not found: [$originalCommand]"
                }
                // The closing single quote is the last character of the command.
                val modifiedCommand = originalCommand.substring(0, markerIndex) +
                    "echo \$PATH > ${outputFile.absolutePath}'"

                val sessionName = "path-integ-${System.currentTimeMillis()}"
                val session = sessionManager.createSession(
                    sessionName,
                    TmuxStartCommand(modifiedCommand),
                )
                createdSessions.add(session)

                AsgardAwaitility.wait()
                    .pollDelay(100.milliseconds)
                    .atMost(5.seconds)
                    .until { outputFile.exists() }

                val capturedPath = outputFile.readText().trim()

                capturedPath shouldContain callbackScriptsDir.path
            }
        }
    }
})
