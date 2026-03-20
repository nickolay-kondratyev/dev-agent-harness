package com.glassthought.shepherd.core.supporting.git

import com.asgard.core.processRunner.ProcessRunner

/**
 * Encapsulates the shared git staging and commit pattern used by both
 * [FinalCommitUseCaseImpl][com.glassthought.shepherd.usecase.finalcommit.FinalCommitUseCaseImpl]
 * and [CommitPerSubPart].
 *
 * Flow:
 * 1. [stageAll] — `git add -A` with error handling via [gitOperationFailureUseCase]
 * 2. [hasStagedChanges] — `git diff --cached --quiet` (exit 0 = no changes, non-zero = changes)
 * 3. [commit] — runs caller-specified commit args with error handling
 *
 * Callers control commit message, author, and other flags by passing the appropriate
 * args to [commit]. This keeps the helper generic.
 */
class GitStagingCommitHelper(
    private val processRunner: ProcessRunner,
    private val gitOperationFailureUseCase: GitOperationFailureUseCase,
    private val gitCommandBuilder: GitCommandBuilder = GitCommandBuilder(),
) {

    /**
     * Stages all changes via `git add -A`.
     *
     * On failure, delegates to [gitOperationFailureUseCase] with the given [failureContext].
     */
    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    suspend fun stageAll(failureContext: GitFailureContext) {
        val command = gitCommandBuilder.build("add", "-A")
        try {
            processRunner.runProcess(*command)
        } catch (e: Exception) {
            gitOperationFailureUseCase.handleGitFailure(
                gitCommand = command.toList(),
                errorOutput = e.message ?: "unknown",
                context = failureContext,
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
    suspend fun hasStagedChanges(): Boolean {
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
     * Executes a git commit command built from the given [commitArgs].
     *
     * [commitArgs] are the arguments after "git" (e.g., `"commit", "-m", "message"` or
     * `"commit", "--author=...", "-m", "message"`). They are passed to [GitCommandBuilder.build]
     * which prepends "git" (and optionally "-C <dir>").
     *
     * On failure, delegates to [gitOperationFailureUseCase] with the given [failureContext].
     */
    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    suspend fun commit(vararg commitArgs: String, failureContext: GitFailureContext) {
        val command = gitCommandBuilder.build(*commitArgs)
        try {
            processRunner.runProcess(*command)
        } catch (e: Exception) {
            gitOperationFailureUseCase.handleGitFailure(
                gitCommand = command.toList(),
                errorOutput = e.message ?: "unknown",
                context = failureContext,
            )
        }
    }
}
