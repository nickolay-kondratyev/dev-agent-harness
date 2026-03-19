package com.glassthought.shepherd.core.executor

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import java.nio.file.Files
import java.nio.file.Path

/**
 * Gate 5: Part Completion Guard — validates no pending critical/important feedback on reviewer PASS.
 *
 * After reviewer signals PASS and PUBLIC.md validation passes, this guard checks the
 * `__feedback/pending/` directory for `critical__*` and `important__*` files. If any are found,
 * the part cannot complete — the reviewer signaling PASS with unresolved blocking feedback is
 * a broken-agent condition (no retry).
 *
 * Remaining `optional__*` files are acceptable — the guard moves them to `addressed/`
 * (implicitly accepted as skipped by the reviewer).
 *
 * See granular-feedback-loop.md R8 (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).
 */
@AnchorPoint("ap.EKFNu5DoQcASJYo4pmgdD.E")
class PartCompletionGuard {

    /**
     * Validates that the pending feedback directory contains no critical or important items.
     *
     * @param pendingDir path to `__feedback/pending/`
     * @param addressedDir path to `__feedback/addressed/`
     * @return [GuardResult.Passed] if no blocking items remain (optional files moved to addressed),
     *   [GuardResult.Failed] if critical or important files are present.
     */
    @Suppress("ReturnCount")
    fun validate(pendingDir: Path, addressedDir: Path): GuardResult {
        if (!Files.exists(pendingDir) || !Files.isDirectory(pendingDir)) {
            // No pending directory means no feedback items — guard passes.
            return GuardResult.Passed
        }

        val pendingFiles = Files.list(pendingDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .toList()
        }

        val blockingFiles = pendingFiles.filter { fileName ->
            fileName.startsWith(ProtocolVocabulary.SeverityPrefix.CRITICAL) ||
                fileName.startsWith(ProtocolVocabulary.SeverityPrefix.IMPORTANT)
        }

        if (blockingFiles.isNotEmpty()) {
            return GuardResult.Failed(
                "Reviewer signaled pass with unaddressed critical/important feedback items in pending/: " +
                    blockingFiles.joinToString(", ")
            )
        }

        // Move remaining optional files to addressed/ (implicitly accepted as skipped).
        val optionalFiles = pendingFiles.filter { fileName ->
            fileName.startsWith(ProtocolVocabulary.SeverityPrefix.OPTIONAL)
        }
        if (optionalFiles.isNotEmpty()) {
            Files.createDirectories(addressedDir)
            for (fileName in optionalFiles) {
                Files.move(pendingDir.resolve(fileName), addressedDir.resolve(fileName))
            }
        }

        return GuardResult.Passed
    }

    sealed class GuardResult {
        object Passed : GuardResult()
        data class Failed(val message: String) : GuardResult()
    }
}
