package com.glassthought.shepherd.usecase.finalcommit

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.supporting.git.GitCommandBuilder
import com.glassthought.shepherd.core.supporting.git.GitFailureContext
import com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase

/**
 * Production implementation of [FinalCommitUseCase].
 *
 * Flow:
 * 1. `git add -A` — stage all remaining changes
 * 2. `git diff --cached --quiet` — if exit 0 (no changes) skip commit
 * 3. `git commit -m "[shepherd] final-state-commit"` — no author attribution (no sub-part context)
 *
 * On git failure (except diff check), delegates to [GitOperationFailureUseCase].
 */
internal class FinalCommitUseCaseImpl(
    outFactory: OutFactory,
    private val processRunner: ProcessRunner,
    private val gitOperationFailureUseCase: GitOperationFailureUseCase,
    private val gitCommandBuilder: GitCommandBuilder = GitCommandBuilder(),
) : FinalCommitUseCase {

    private val out = outFactory.getOutForClass(FinalCommitUseCaseImpl::class)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun commitIfDirty() {
        out.info("final_commit_triggered")

        stageAll()

        if (!hasStagedChanges()) {
            out.info("no_staged_changes_skipping_final_commit")
            return
        }

        commit()

        out.info("final_commit_created")
    }

    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    private suspend fun stageAll() {
        val command = gitCommandBuilder.build("add", "-A")
        try {
            processRunner.runProcess(*command)
        } catch (e: Exception) {
            gitOperationFailureUseCase.handleGitFailure(
                gitCommand = command.toList(),
                errorOutput = e.message ?: "unknown",
                context = FAILURE_CONTEXT,
            )
        }
    }

    /**
     * Returns true when there are staged changes to commit.
     *
     * `git diff --cached --quiet` exits 0 when the index matches HEAD (no changes),
     * and non-zero when there are staged changes. Since [ProcessRunner] throws on
     * non-zero exit, an exception here means changes exist.
     */
    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    private suspend fun hasStagedChanges(): Boolean {
        return try {
            processRunner.runProcess(*gitCommandBuilder.build("diff", "--cached", "--quiet"))
            // Exit 0 → no changes
            false
        } catch (_: Exception) {
            // Non-zero exit → changes exist
            true
        }
    }

    /**
     * Executes `git commit -m "[shepherd] final-state-commit"`.
     * On failure, delegates to [gitOperationFailureUseCase].
     */
    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    private suspend fun commit() {
        val command = gitCommandBuilder.build("commit", "-m", COMMIT_MESSAGE)
        try {
            processRunner.runProcess(*command)
        } catch (e: Exception) {
            gitOperationFailureUseCase.handleGitFailure(
                gitCommand = command.toList(),
                errorOutput = e.message ?: "unknown",
                context = FAILURE_CONTEXT,
            )
        }
    }

    companion object {
        internal const val COMMIT_MESSAGE = "[shepherd] final-state-commit"

        private val FAILURE_CONTEXT = GitFailureContext(
            partName = "final-commit",
            subPartName = "final-state",
            iterationNumber = 0,
        )
    }
}
