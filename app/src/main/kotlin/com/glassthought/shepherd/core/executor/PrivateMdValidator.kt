package com.glassthought.shepherd.core.executor

import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates that PRIVATE.md exists and is non-empty after an agent signals SelfCompacted.
 *
 * Per spec (ref.ap.8nwz2AHf503xwq8fKuLcl.E): after the agent signals `SelfCompacted`,
 * the executor verifies the agent's `private/PRIVATE.md` exists and is non-empty **before**
 * proceeding with session rotation. If missing/empty, the agent is considered broken (no retry).
 *
 * Returns a validation result rather than throwing — the caller maps failure to
 * [PartResult.AgentCrashed].
 */
class PrivateMdValidator {

    /**
     * Validates that the file at [privateMdPath] exists and has non-zero size.
     *
     * @return [ValidationResult.Valid] if the file exists and is non-empty,
     *   [ValidationResult.Invalid] with a descriptive message otherwise.
     */
    fun validate(privateMdPath: Path, subPartName: String): ValidationResult = when {
        !Files.exists(privateMdPath) -> ValidationResult.Invalid(
            "Agent [$subPartName] failed to produce PRIVATE.md at path=[$privateMdPath]"
        )
        Files.size(privateMdPath) == 0L -> ValidationResult.Invalid(
            "Agent [$subPartName] produced empty PRIVATE.md at path=[$privateMdPath]"
        )
        else -> ValidationResult.Valid
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }
}
