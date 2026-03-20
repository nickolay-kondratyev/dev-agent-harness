package com.glassthought.shepherd.core.agent.adapter

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.infra.DispatcherProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds

/**
 * Polling configuration for GUID resolution via [FilesystemGuidScanner].
 *
 * @param resolveTimeoutMs Total polling window in milliseconds (default 45 seconds).
 * @param pollIntervalMs Delay between poll attempts in milliseconds (default 500 ms).
 */
data class GuidResolutionConfig(
    val resolveTimeoutMs: Long = 45_000L,
    val pollIntervalMs: Long = 500L,
)

/**
 * Filesystem-backed [GuidScanner]: walks [claudeProjectsDir] recursively
 * for `*.jsonl` files and returns those containing the GUID string.
 */
private class FilesystemGuidScanner(
    private val claudeProjectsDir: Path,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.standard(),
) : GuidScanner {
    override suspend fun scan(guid: HandshakeGuid): List<Path> = withContext(dispatcherProvider.io()) {
        Files.walk(claudeProjectsDir)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { it.extension == "jsonl" }
                    .filter { it.readText().contains(guid.value) }
                    // java.util.stream.Stream.toList() — available since Java 16, no import needed
                    .toList()
            }
    }
}

/**
 * Claude Code implementation of [AgentTypeAdapter].
 *
 * Merges the responsibilities of the former `ClaudeCodeAgentStarter` (command building)
 * and `ClaudeCodeAgentSessionIdResolver` (session ID resolution via JSONL scanning).
 *
 * **buildStartCommand** — builds the `claude` CLI command for **interactive mode** (no `-p`/`--print`):
 * - `TICKET_SHEPHERD_HANDSHAKE_GUID`, `TICKET_SHEPHERD_SERVER_PORT`, and PATH env var exports
 * - `cd` to working directory
 * - `unset CLAUDECODE` (nested-session detection workaround)
 * - `--system-prompt-file <path>` (required, see SpawnTmuxAgentSessionUseCase spec)
 * - Model, tools, dangerously-skip-permissions flags
 * - Bootstrap message embedded as a positional initial prompt argument
 *
 * **resolveSessionId** — polls JSONL files under `~/.claude/projects/` for files containing the
 * GUID string. The matching filename (minus `.jsonl` extension) is the session ID.
 * Called after `/signal/started` is received, so the GUID is guaranteed to be present.
 *
 * Use [create] factory for production wiring. The primary constructor is `internal` so tests
 * can inject a fake [GuidScanner].
 *
 * @param guidScanner Strategy for scanning for GUID matches.
 * @param outFactory Factory for structured logging.
 * @param serverPort The Shepherd HTTP server port, exported as `TICKET_SHEPHERD_SERVER_PORT` into each agent tmux session.
 * @param callbackScriptsDir Validated directory containing `callback_shepherd.signal.sh`, added to PATH in the tmux session.
 * @param resolveTimeoutMs Total polling window in milliseconds (default 45 seconds).
 * @param pollIntervalMs Delay between poll attempts in milliseconds (default 500 ms).
 */
@AnchorPoint("ap.gCgRdmWd9eTGXPbHJvyxI.E")
class ClaudeCodeAdapter internal constructor(
    private val guidScanner: GuidScanner,
    outFactory: OutFactory,
    private val serverPort: Int,
    private val callbackScriptsDir: CallbackScriptsDir,
    private val glmConfig: GlmConfig? = null,
    private val resolveTimeoutMs: Long = 45_000L,
    private val pollIntervalMs: Long = 500L,
) : AgentTypeAdapter {

    private val out = outFactory.getOutForClass(ClaudeCodeAdapter::class)

    override fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand {
        val parts = mutableListOf("claude")

        parts.add("--model")
        parts.add(params.model)

        if (params.tools.isNotEmpty()) {
            // [--tools]: restricts available tool set and reduces context window size.
            // Distinct from --allowedTools which only pre-approves permissions but keeps
            // all tools available (and in the context window).
            parts.add("--tools")
            parts.add(params.tools.joinToString(","))
        }

        if (params.systemPromptFilePath != null) {
            val flag = if (params.appendSystemPrompt) {
                "--append-system-prompt-file"
            } else {
                "--system-prompt-file"
            }
            parts.add(flag)
            parts.add(params.systemPromptFilePath)
        }

        // [--dangerously-skip-permissions]: Always enabled. Docker-only invariant enforced
        // by EnvironmentValidator at startup (ref.ap.A8WqG9oplNTpsW7YqoIyX.E).
        parts.add("--dangerously-skip-permissions")

        // Bootstrap message as positional initial prompt argument (after all flags).
        // WHY append GUID: The bootstrap message is the first user message recorded in the
        // Claude CLI's JSONL session file. FilesystemGuidScanner searches JSONL content for
        // the handshake GUID to resolve the session ID. Without the GUID in the message,
        // the scanner never finds a match and resolveSessionId times out.
        val bootstrapWithGuid = "${params.bootstrapMessage}\n\n" +
            "[HARNESS_GUID: ${params.handshakeGuid.value}]"
        // Shell-escaped to handle special characters in the message.
        parts.add(shellQuote(bootstrapWithGuid))

        val claudeCommand = parts.joinToString(" ")
        // [unset CLAUDECODE]: Claude Code refuses to start when the CLAUDECODE env var is set
        // (nested session detection). The harness spawns Claude in a tmux session which inherits
        // the parent environment, so we must explicitly unset it.
        //
        // [TICKET_SHEPHERD_HANDSHAKE_GUID]: exported so callback scripts can include it in every
        // HTTP callback, identifying this agent session to the harness server.
        //
        // [TICKET_SHEPHERD_SERVER_PORT]: exported so callback scripts know which port to POST to.
        //
        // [PATH += callbackScriptsDir]: ensures callback_shepherd.signal.sh is on PATH for the agent.
        //
        // [GLM env vars]: When glmConfig is provided, env var exports are prepended to redirect
        // the spawned `claude` CLI to the GLM (Z.AI) Anthropic-compatible endpoint.
        // See ref.ap.8BYTb6vcyAzpWavQguBrb.E for config details.
        val glmPrefix = if (glmConfig != null) "${glmConfig.toEnvVarExports()} && " else ""
        val innerCommand = "${glmPrefix}cd ${params.workingDir} && " +
            "unset CLAUDECODE && " +
            "export ${Constants.AGENT_COMM.HANDSHAKE_GUID_ENV_VAR}=${params.handshakeGuid.value} && " +
            "export ${Constants.AGENT_COMM.SERVER_PORT_ENV_VAR}=$serverPort && " +
            "export PATH=\$PATH:${callbackScriptsDir.path} && " +
            claudeCommand
        val fullCommand = "bash -c '${escapeForBashC(innerCommand)}'"

        return TmuxStartCommand(fullCommand)
    }

    override suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String {
        out.info(
            "resolving_session_id_with_polling",
            Val(handshakeGuid.value, ValType.STRING_USER_AGNOSTIC),
        )

        val matchingFiles: List<Path> = try {
            withTimeout(resolveTimeoutMs.milliseconds) {
                pollUntilFound(handshakeGuid)
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Timed out after ${resolveTimeoutMs}ms waiting for GUID [$handshakeGuid] to appear in any JSONL file",
                e,
            )
        }

        return when (matchingFiles.size) {
            0 -> error(
                "No JSONL file contains GUID [$handshakeGuid]"
            )
            1 -> {
                val sessionId = matchingFiles.single().nameWithoutExtension
                out.info(
                    "session_id_resolved",
                    Val(sessionId, ValType.STRING_USER_AGNOSTIC),
                )
                sessionId
            }
            else -> {
                val filenames = matchingFiles.map { it.fileName.toString() }
                error(
                    "Ambiguous GUID match: GUID [$handshakeGuid] found in multiple files $filenames"
                )
            }
        }
    }

    /**
     * Polls [guidScanner] until at least one match is found, then returns the matches.
     * Must be called inside a coroutine scope with a timeout; the timeout cancels the loop.
     */
    private suspend fun pollUntilFound(guid: HandshakeGuid): List<Path> {
        while (true) {
            val matches = guidScanner.scan(guid)
            out.debug("guid_poll_attempt") {
                listOf(
                    Val(guid.value, ValType.STRING_USER_AGNOSTIC),
                    Val(matches.size.toString(), ValType.STRING_USER_AGNOSTIC),
                )
            }
            if (matches.isNotEmpty()) {
                return matches
            }
            delay(pollIntervalMs.milliseconds)
        }
    }

    companion object {
        /**
         * Production factory: creates a [ClaudeCodeAdapter] backed by [FilesystemGuidScanner].
         *
         * @param claudeProjectsDir Root directory to scan (typically `~/.claude/projects`).
         * @param outFactory Factory for structured logging.
         * @param serverPort The Shepherd HTTP server port to export into each agent's tmux session.
         * @param callbackScriptsDir Validated directory containing callback scripts, added to PATH.
         * @param glmConfig Optional GLM config to redirect agents to Z.AI instead of Anthropic.
         * @param resolutionConfig Polling timing config for GUID resolution (default: [GuidResolutionConfig]).
         * @param dispatcherProvider Coroutine dispatcher provider for IO operations.
         */
        fun create(
            claudeProjectsDir: Path,
            outFactory: OutFactory,
            serverPort: Int,
            callbackScriptsDir: CallbackScriptsDir,
            glmConfig: GlmConfig? = null,
            resolutionConfig: GuidResolutionConfig = GuidResolutionConfig(),
            dispatcherProvider: DispatcherProvider = DispatcherProvider.standard(),
        ): ClaudeCodeAdapter = ClaudeCodeAdapter(
            guidScanner = FilesystemGuidScanner(claudeProjectsDir, dispatcherProvider),
            outFactory = outFactory,
            serverPort = serverPort,
            callbackScriptsDir = callbackScriptsDir,
            glmConfig = glmConfig,
            resolveTimeoutMs = resolutionConfig.resolveTimeoutMs,
            pollIntervalMs = resolutionConfig.pollIntervalMs,
        )

        /**
         * Escapes a string for safe inclusion in a single-quoted bash -c command.
         * Replaces single quotes with the idiom: end quote, escaped quote, start quote.
         */
        private fun escapeForBashC(value: String): String =
            value.replace("'", "'\\''")

        // Quotes a string for use as a shell argument using double quotes.
        // Escapes characters that are special inside double quotes.
        private const val DOLLAR = '$'

        private fun shellQuote(value: String): String {
            val escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace(DOLLAR.toString(), "\\$DOLLAR")
                .replace("`", "\\`")
                .replace("!", "\\!")
            return "\"$escaped\""
        }
    }
}
