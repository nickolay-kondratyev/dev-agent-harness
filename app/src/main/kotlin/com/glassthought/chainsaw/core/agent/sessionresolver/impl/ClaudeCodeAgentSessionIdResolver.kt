package com.glassthought.chainsaw.core.agent.sessionresolver.impl

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.chainsaw.core.data.AgentType
import com.glassthought.chainsaw.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.chainsaw.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.chainsaw.core.agent.sessionresolver.AgentSessionIdResolver
import kotlinx.coroutines.Dispatchers
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
 * Scans for JSONL files containing a given GUID string.
 *
 * Extracted as an interface so tests can inject a fake that controls
 * scan results on successive calls without touching the filesystem.
 *
 * ref.ap.gCgRdmWd9eTGXPbHJvyxI.E
 */
interface GuidScanner {
    /** Returns all JSONL [Path]s whose content contains [guid]. */
    suspend fun scan(guid: HandshakeGuid): List<Path>
}

/**
 * Filesystem-backed [GuidScanner]: walks [claudeProjectsDir] recursively
 * for `*.jsonl` files and returns those containing the GUID string.
 */
private class FilesystemGuidScanner(private val claudeProjectsDir: Path) : GuidScanner {
    override suspend fun scan(guid: HandshakeGuid): List<Path> = withContext(Dispatchers.IO) {
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
 * Discovers Claude Code session IDs by scanning JSONL session files for a GUID marker.
 *
 * Polls [GuidScanner] in a loop with a [resolveTimeoutMs]-millisecond timeout and
 * [pollIntervalMs]-millisecond delay between retries, because the JSONL file is written
 * asynchronously by Claude Code after the TMUX `send-keys` message.
 *
 * Exactly one match is required; zero (after timeout) or multiple matches result in an
 * [IllegalStateException].
 *
 * @param claudeProjectsDir Root directory to scan (typically `~/.claude/projects`).
 * @param outFactory Factory for structured logging.
 * @param resolveTimeoutMs Total polling window in milliseconds (default 45 seconds).
 * @param pollIntervalMs Delay between poll attempts in milliseconds (default 500 ms).
 *
 */
@AnchorPoint("ap.gCgRdmWd9eTGXPbHJvyxI.E")
class ClaudeCodeAgentSessionIdResolver(
    claudeProjectsDir: Path,
    outFactory: OutFactory,
    private val resolveTimeoutMs: Long = 45_000L,
    private val pollIntervalMs: Long = 500L,
) : AgentSessionIdResolver {

    // Internal constructor allows tests to inject a fake GuidScanner.
    constructor(
        guidScanner: GuidScanner,
        outFactory: OutFactory,
        resolveTimeoutMs: Long = 45_000L,
        pollIntervalMs: Long = 500L,
    ) : this(
        claudeProjectsDir = PLACEHOLDER_PATH,
        outFactory = outFactory,
        resolveTimeoutMs = resolveTimeoutMs,
        pollIntervalMs = pollIntervalMs,
    ) {
        this.guidScanner = guidScanner
    }

    private val out = outFactory.getOutForClass(ClaudeCodeAgentSessionIdResolver::class)

    // Backing field set via internal constructor for test injection; otherwise built from claudeProjectsDir.
    private var guidScanner: GuidScanner = FilesystemGuidScanner(claudeProjectsDir)

    override suspend fun resolveSessionId(guid: HandshakeGuid): ResumableAgentSessionId {
        out.info(
            "resolving_session_id_with_polling",
            Val(guid.value, ValType.STRING_USER_AGNOSTIC),
        )

        val matchingFiles: List<Path> = try {
            withTimeout(resolveTimeoutMs.milliseconds) {
                pollUntilFound(guid)
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Timed out after ${resolveTimeoutMs}ms waiting for GUID [$guid] to appear in any JSONL file",
                e,
            )
        }

        return when (matchingFiles.size) {
            0 -> throw IllegalStateException(
                "No JSONL file contains GUID [$guid]"
            )
            1 -> {
                val sessionId = matchingFiles.single().nameWithoutExtension
                out.info(
                    "session_id_resolved",
                    Val(sessionId, ValType.STRING_USER_AGNOSTIC),
                )
                ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)
            }
            else -> {
                val filenames = matchingFiles.map { it.fileName.toString() }
                throw IllegalStateException(
                    "Ambiguous GUID match: GUID [$guid] found in multiple files $filenames"
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
        // Sentinel value used as a placeholder path in the internal test constructor.
        // The FilesystemGuidScanner built from this path is never called when a
        // custom guidScanner is injected.
        private val PLACEHOLDER_PATH: Path = Path.of("/dev/null")
    }
}
