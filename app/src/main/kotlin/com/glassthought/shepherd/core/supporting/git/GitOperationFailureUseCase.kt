package com.glassthought.shepherd.core.supporting.git

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase

/**
 * Handles git operation failures with an index.lock fast-path and fail-fast fallback.
 *
 * **V1 approach** (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E):
 * - **Fast-path**: When stderr contains `index.lock` or `unable to lock` AND `.git/index.lock`
 *   exists on disk, delete the lock file and retry the original command.
 * - **Fail-fast**: For all other failures, or when the retry also fails, escalate to
 *   [FailedToExecutePlanUseCase] with a [PartResult.FailedWorkflow].
 *
 * See `doc/core/git.md` § Git Operation Failure Handling.
 */
// ap.3W25hwJNB64sPy63Nc3OV.E
fun interface GitOperationFailureUseCase {

    /**
     * Handles a failed git operation.
     *
     * If the failure is recoverable (index.lock), attempts automatic recovery and retries.
     * Otherwise, escalates via [FailedToExecutePlanUseCase] (which terminates the process).
     *
     * @param gitCommand The git command args that failed (e.g., `["git", "add", "-A"]`).
     * @param errorOutput The stderr from the failed command.
     * @param context Workflow context for error reporting.
     */
    suspend fun handleGitFailure(
        gitCommand: List<String>,
        errorOutput: String,
        context: GitFailureContext,
    )

    companion object {
        fun standard(
            outFactory: OutFactory,
            processRunner: ProcessRunner,
            failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
            indexLockFileOperations: GitIndexLockFileOperations,
        ): GitOperationFailureUseCase = GitOperationFailureUseCaseImpl(
            outFactory = outFactory,
            processRunner = processRunner,
            failedToExecutePlanUseCase = failedToExecutePlanUseCase,
            indexLockFileOperations = indexLockFileOperations,
        )
    }
}

/**
 * Workflow context for git failure error messages.
 *
 * Provides enough information for a human to locate the failure point in the workflow.
 */
data class GitFailureContext(
    val partName: String,
    val subPartName: String,
    val iterationNumber: Int,
)

/**
 * Default implementation of [GitOperationFailureUseCase].
 *
 * Flow:
 * 1. Check if error indicates index.lock issue AND lock file exists → delete + retry.
 * 2. If retry succeeds → return normally.
 * 3. If retry fails OR not an index.lock issue → log details and escalate via [FailedToExecutePlanUseCase].
 */
class GitOperationFailureUseCaseImpl(
    outFactory: OutFactory,
    private val processRunner: ProcessRunner,
    private val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
    private val indexLockFileOperations: GitIndexLockFileOperations,
) : GitOperationFailureUseCase {

    private val out = outFactory.getOutForClass(GitOperationFailureUseCaseImpl::class)

    override suspend fun handleGitFailure(
        gitCommand: List<String>,
        errorOutput: String,
        context: GitFailureContext,
    ) {
        if (isIndexLockError(errorOutput) && indexLockFileOperations.indexLockExists()) {
            out.info(
                "index_lock_detected_attempting_recovery",
                Val(gitCommand.joinToString(" "), ValType.SHELL_COMMAND),
            )

            indexLockFileOperations.deleteIndexLock()

            val retryResult = retryCommand(gitCommand)
            if (retryResult != null) {
                out.info(
                    "index_lock_recovery_succeeded",
                    Val(gitCommand.joinToString(" "), ValType.SHELL_COMMAND),
                )
                return
            }

            // Retry failed — fall through to fail-fast
            out.info(
                "index_lock_recovery_failed_escalating",
                Val(gitCommand.joinToString(" "), ValType.SHELL_COMMAND),
            )
        }

        failFast(gitCommand, errorOutput, context)
    }

    private fun isIndexLockError(errorOutput: String): Boolean {
        val lowerError = errorOutput.lowercase()
        return lowerError.contains(INDEX_LOCK_MARKER) || lowerError.contains(UNABLE_TO_LOCK_MARKER)
    }

    /**
     * Retries the git command. Returns stdout on success, null on failure.
     */
    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    private suspend fun retryCommand(gitCommand: List<String>): String? {
        return try {
            processRunner.runProcess(*gitCommand.toTypedArray())
        } catch (e: Exception) {
            out.info("retry_command_failed") {
                listOf(Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC))
            }
            null
        }
    }

    private suspend fun failFast(
        gitCommand: List<String>,
        errorOutput: String,
        context: GitFailureContext,
    ): Nothing {
        val gitStatus = getGitStatusBestEffort()

        out.info("git_operation_failed") {
            listOf(
                Val(gitCommand.joinToString(" "), ValType.SHELL_COMMAND),
                Val(errorOutput, ValType.STRING_USER_AGNOSTIC),
                Val(gitStatus, ValType.STRING_USER_AGNOSTIC),
            )
        }

        val reason = buildString {
            append("Git operation failed: ${gitCommand.joinToString(" ")}")
            append("\nPart: ${context.partName}, Sub-part: ${context.subPartName}")
            append(", Iteration: ${context.iterationNumber}")
            append("\nStderr: $errorOutput")
            append("\nGit status: $gitStatus")
        }

        failedToExecutePlanUseCase.handleFailure(PartResult.FailedWorkflow(reason))
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun getGitStatusBestEffort(): String {
        return try {
            processRunner.runProcess("git", "status")
        } catch (e: Exception) {
            "<unable to retrieve git status: ${e.message}>"
        }
    }

    companion object {
        private const val INDEX_LOCK_MARKER = "index.lock"
        private const val UNABLE_TO_LOCK_MARKER = "unable to lock"
    }
}
