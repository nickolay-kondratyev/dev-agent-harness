package com.glassthought.chainsaw.core.wingman

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.streams.toList

/**
 * Discovers Claude Code session IDs by scanning JSONL session files for a GUID marker.
 *
 * Walks [claudeProjectsDir] recursively for `*.jsonl` files and checks each file's
 * content for the given GUID string. Exactly one match is required; zero or multiple
 * matches result in an [IllegalStateException].
 *
 * @param claudeProjectsDir Root directory to scan (typically `~/.claude/projects`).
 * @param outFactory Factory for structured logging.
 */
class ClaudeCodeWingman(
    private val claudeProjectsDir: Path,
    outFactory: OutFactory,
) : Wingman {

    private val out = outFactory.getOutForClass(ClaudeCodeWingman::class)

    override suspend fun resolveSessionId(guid: String): String {
        out.info(
            "resolving_session_id",
            Val(guid, ValType.STRING_USER_AGNOSTIC),
            Val(claudeProjectsDir.toString(), ValType.FILE_PATH_STRING),
        )

        val matchingFiles = Files.walk(claudeProjectsDir)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { it.extension == "jsonl" }
                    .filter { it.readText().contains(guid) }
                    .toList()
            }

        out.debug("guid_search_completed") {
            listOf(
                Val(guid, ValType.STRING_USER_AGNOSTIC),
                Val(matchingFiles.size.toString(), ValType.STRING_USER_AGNOSTIC),
            )
        }

        return when (matchingFiles.size) {
            0 -> throw IllegalStateException(
                "No JSONL file contains GUID [${guid}] in directory [${claudeProjectsDir}]"
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
                throw IllegalStateException(
                    "Ambiguous GUID match: GUID [${guid}] found in multiple files $filenames"
                )
            }
        }
    }
}
