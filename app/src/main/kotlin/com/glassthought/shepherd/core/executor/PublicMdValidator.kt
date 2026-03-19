package com.glassthought.shepherd.core.executor

import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates that PUBLIC.md exists and is non-empty after an agent signals Done.
 *
 * Per spec (ref.ap.THDW9SHzs1x2JN9YP9OYU.E): after every [AgentSignal.Done], the executor
 * verifies the signaling agent's `comm/out/PUBLIC.md` exists and is non-empty **before**
 * proceeding. If missing/empty, the agent is considered broken (no retry).
 *
 * Returns a validation result rather than throwing — the caller maps failure to
 * [PartResult.AgentCrashed].
 */
class PublicMdValidator {

    /**
     * Validates that the file at [publicMdPath] exists and has non-zero size.
     *
     * @return [ValidationResult.Valid] if the file exists and is non-empty,
     *   [ValidationResult.Invalid] with a descriptive message otherwise.
     */
    fun validate(publicMdPath: Path, subPartName: String): ValidationResult = when {
        !Files.exists(publicMdPath) -> ValidationResult.Invalid(
            "Agent [$subPartName] failed to produce PUBLIC.md at path=[$publicMdPath]"
        )
        Files.size(publicMdPath) == 0L -> ValidationResult.Invalid(
            "Agent [$subPartName] produced empty PUBLIC.md at path=[$publicMdPath]"
        )
        else -> ValidationResult.Valid
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }
}
