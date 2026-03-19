package com.glassthought.shepherd.core.supporting.git

import com.glassthought.shepherd.core.supporting.ticket.TicketData
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves the next try-N number for a ticket by scanning `.ai_out/` directories.
 *
 * `.ai_out/` is the single source of truth for try-N resolution (see doc/core/git.md).
 * N is the first value where the corresponding `.ai_out/{branchName}/` directory does not exist.
 */
fun interface TryNResolver {

    /**
     * Returns the next available try number for the given [ticketData].
     *
     * Scans `.ai_out/` directories starting from try-1, returning the first N
     * where `.ai_out/{branchName}/` does not exist.
     *
     * @return The next available try number (>= 1).
     */
    suspend fun resolve(ticketData: TicketData): Int
}

/**
 * Default implementation that checks the filesystem under [repoRoot]/.ai_out/.
 */
class TryNResolverImpl(
    private val repoRoot: Path,
) : TryNResolver {

    override suspend fun resolve(ticketData: TicketData): Int {
        var candidateN = FIRST_TRY_NUMBER
        while (true) {
            val branchName = BranchNameBuilder.build(ticketData, candidateN)
            val candidateDir = repoRoot.resolve(AI_OUT_DIR).resolve(branchName)
            if (!Files.isDirectory(candidateDir)) {
                return candidateN
            }
            candidateN++
        }
    }

    companion object {
        private const val AI_OUT_DIR = ".ai_out"
        private const val FIRST_TRY_NUMBER = 1
    }
}
