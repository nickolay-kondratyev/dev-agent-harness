package com.glassthought.shepherd.core.agent.contextwindow

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.facade.ContextWindowState
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.state.ShepherdObjectMapper
import com.glassthought.shepherd.core.time.Clock
import com.fasterxml.jackson.core.JacksonException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.time.toJavaDuration

/**
 * Reads [ContextWindowState] from the `context_window_slim.json` file written by an external
 * Claude Code hook.
 *
 * File location: `<basePath>/<agentSessionId>/context_window_slim.json`
 *
 * @param basePath Root directory for session state files. Injectable for testability;
 *   defaults to `${HOME}/.vintrin_env/claude_code/session`.
 * @param clock Wall-clock source for staleness detection.
 * @param harnessTimeoutConfig Supplies [HarnessTimeoutConfig.contextFileStaleTimeout].
 * @param outFactory Logger factory.
 */
class ClaudeCodeContextWindowStateReader(
    private val basePath: Path = defaultBasePath(),
    private val clock: Clock,
    private val harnessTimeoutConfig: HarnessTimeoutConfig,
    private val outFactory: OutFactory,
) : ContextWindowStateReader {

    private val out = outFactory.getOutForClass(ClaudeCodeContextWindowStateReader::class)
    private val objectMapper = ShepherdObjectMapper.create()

    override suspend fun read(agentSessionId: String): ContextWindowState {
        val filePath = basePath.resolve(agentSessionId).resolve(CONTEXT_WINDOW_FILE_NAME)

        if (!Files.exists(filePath)) {
            throw ContextWindowStateUnavailableException(
                "context_window_slim.json not found at [$filePath]. " +
                    "The external hook is likely not configured."
            )
        }

        val dto = parseAndValidate(filePath)
        val fileTimestamp = parseTimestamp(dto.fileUpdatedTimestamp, filePath)

        val staleThreshold = clock.now().minus(harnessTimeoutConfig.contextFileStaleTimeout.toJavaDuration())

        if (fileTimestamp.isBefore(staleThreshold)) {
            out.warn(
                "context_window_slim.json is stale",
                Val(agentSessionId, ValType.STRING_USER_AGNOSTIC),
                Val(dto.fileUpdatedTimestamp, ValType.STRING_USER_AGNOSTIC),
            )
            return ContextWindowState(remainingPercentage = null)
        }

        return ContextWindowState(remainingPercentage = dto.remainingPercentage)
    }

    /**
     * Parses the JSON file and validates that all required fields are present and in range.
     *
     * WHY nullable DTO fields + explicit validation: Jackson KotlinModule silently defaults
     * missing primitive `Int` fields to 0 instead of throwing, so we use nullable fields
     * and validate here to get clear error messages.
     */
    private fun parseAndValidate(filePath: Path): ValidatedContextWindowSlim {
        val dto = parseFile(filePath)

        val fileUpdatedTimestamp = requireField(dto.fileUpdatedTimestamp, "file_updated_timestamp", filePath)
        val remainingPercentage = requireField(dto.remainingPercentage, "remaining_percentage", filePath)

        return ValidatedContextWindowSlim(
            fileUpdatedTimestamp = fileUpdatedTimestamp,
            remainingPercentage = validateBounds(remainingPercentage, filePath),
        )
    }

    private fun <T : Any> requireField(value: T?, fieldName: String, filePath: Path): T {
        return value ?: throw ContextWindowStateUnavailableException(
            "Failed to parse context_window_slim.json at [$filePath]: " +
                "missing required field [$fieldName]"
        )
    }

    private fun validateBounds(remainingPercentage: Int, filePath: Path): Int {
        if (remainingPercentage !in REMAINING_PERCENTAGE_RANGE) {
            throw ContextWindowStateUnavailableException(
                "Failed to parse context_window_slim.json at [$filePath]: " +
                    "remaining_percentage [$remainingPercentage] is outside valid range $REMAINING_PERCENTAGE_RANGE"
            )
        }
        return remainingPercentage
    }

    private fun parseFile(filePath: Path): ContextWindowSlimDto {
        try {
            return objectMapper.readValue(
                Files.readString(filePath),
                ContextWindowSlimDto::class.java,
            )
        } catch (e: JacksonException) {
            throw ContextWindowStateUnavailableException(
                "Failed to parse context_window_slim.json at [$filePath]: ${e.message}",
                e,
            )
        } catch (e: IOException) {
            throw ContextWindowStateUnavailableException(
                "Failed to read context_window_slim.json at [$filePath]: ${e.message}",
                e,
            )
        }
    }

    private fun parseTimestamp(timestampStr: String, filePath: Path): Instant {
        try {
            return Instant.parse(timestampStr)
        } catch (e: DateTimeParseException) {
            throw ContextWindowStateUnavailableException(
                "Invalid file_updated_timestamp [$timestampStr] in [$filePath]: ${e.message}",
                e,
            )
        }
    }

    /**
     * Validated (non-nullable) representation of [ContextWindowSlimDto] after field presence checks.
     */
    private data class ValidatedContextWindowSlim(
        val fileUpdatedTimestamp: String,
        val remainingPercentage: Int,
    )

    companion object {
        private const val CONTEXT_WINDOW_FILE_NAME = "context_window_slim.json"
        private val REMAINING_PERCENTAGE_RANGE = 0..100

        fun defaultBasePath(): Path = Path.of(
            System.getenv("HOME"),
            ".vintrin_env", "claude_code", "session",
        )
    }
}
