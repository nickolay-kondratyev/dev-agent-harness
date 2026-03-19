package com.glassthought.shepherd.core.supporting.git

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.data.AgentType
import java.nio.file.Path

/**
 * Context provided to [GitCommitStrategy.onSubPartDone] after a sub-part signals done.
 *
 * Contains enough information to build the commit message and author attribution.
 * See doc/core/git.md — "Hook Context" (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).
 */
data class SubPartDoneContext(
    val partName: String,
    val subPartName: String,
    val subPartRole: String,
    val result: String,
    val hasReviewer: Boolean,
    val currentIteration: Int,
    val maxIterations: Int,
    val agentType: AgentType,
    val model: String,
)

/**
 * Strategy for when and how harness-owned git commits are created.
 *
 * The single hook [onSubPartDone] fires after a sub-part signals `result` via
 * `/callback-shepherd/signal/done`, before the next sub-part starts or iteration resumes.
 *
 * See doc/core/git.md — "When Commits Happen — GitCommitStrategy".
 */
fun interface GitCommitStrategy {

    /**
     * Called after a sub-part completes. Implementations decide whether to commit
     * and how to build the commit metadata.
     */
    suspend fun onSubPartDone(context: SubPartDoneContext)

    companion object {
        /**
         * V1 strategy: commits after every sub-part done signal.
         *
         * Skips the commit when there are no staged changes (empty diff).
         */
        fun commitPerSubPart(
            outFactory: OutFactory,
            processRunner: ProcessRunner,
            gitOperationFailureUseCase: GitOperationFailureUseCase,
            hostUsername: String,
            gitUserEmail: String,
            workingDir: Path? = null,
        ): GitCommitStrategy = CommitPerSubPart(
            outFactory = outFactory,
            processRunner = processRunner,
            gitOperationFailureUseCase = gitOperationFailureUseCase,
            hostUsername = hostUsername,
            gitUserEmail = gitUserEmail,
            workingDir = workingDir,
        )
    }
}

/**
 * V1 implementation: commits after every sub-part done signal.
 *
 * Flow:
 * 1. `git add -A` — stage all changes
 * 2. `git diff --cached --quiet` — if exit 0 (no changes) skip commit
 * 3. Build commit message via [CommitMessageBuilder]
 * 4. Build author via [CommitAuthorBuilder]
 * 5. `git commit --author="{author} <{email}>" -m "{message}"`
 *
 * On git failure (except diff check), delegates to [GitOperationFailureUseCase].
 */
internal class CommitPerSubPart(
    outFactory: OutFactory,
    private val processRunner: ProcessRunner,
    private val gitOperationFailureUseCase: GitOperationFailureUseCase,
    private val hostUsername: String,
    private val gitUserEmail: String,
    private val workingDir: Path? = null,
) : GitCommitStrategy {

    private val out = outFactory.getOutForClass(CommitPerSubPart::class)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun onSubPartDone(context: SubPartDoneContext) {
        out.info("commit_per_sub_part_triggered") {
            listOf(
                Val("${context.partName}/${context.subPartName}", ValType.STRING_USER_AGNOSTIC),
                Val(context.result, ValType.STRING_USER_AGNOSTIC),
            )
        }

        stageAll(context)

        if (!hasStagedChanges()) {
            out.info("no_staged_changes_skipping_commit")
            return
        }

        val message = CommitMessageBuilder.build(
            partName = context.partName,
            subPartName = context.subPartName,
            result = context.result,
            hasReviewer = context.hasReviewer,
            currentIteration = context.currentIteration,
            maxIterations = context.maxIterations,
        )

        val authorName = CommitAuthorBuilder.build(
            agentType = context.agentType,
            model = context.model,
            hostUsername = hostUsername,
        )

        commit(authorName = authorName, message = message, context = context)

        out.info("commit_created") {
            listOf(
                Val(message, ValType.STRING_USER_AGNOSTIC),
                Val(authorName, ValType.STRING_USER_AGNOSTIC),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    private suspend fun stageAll(context: SubPartDoneContext) {
        val command = gitCommand("add", "-A")
        try {
            processRunner.runProcess(*command)
        } catch (e: Exception) {
            gitOperationFailureUseCase.handleGitFailure(
                gitCommand = command.toList(),
                errorOutput = e.message ?: "unknown",
                context = toGitFailureContext(context),
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
            processRunner.runProcess(*gitCommand("diff", "--cached", "--quiet"))
            // Exit 0 → no changes
            false
        } catch (_: Exception) {
            // Non-zero exit → changes exist
            true
        }
    }

    /**
     * Executes `git commit`. On failure, delegates to [gitOperationFailureUseCase] which may
     * return normally after successful index.lock recovery (retries the exact same command).
     */
    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    private suspend fun commit(
        authorName: String,
        message: String,
        context: SubPartDoneContext,
    ) {
        val command = gitCommand(
            "commit",
            "--author=$authorName <$gitUserEmail>",
            "-m",
            message,
        )
        try {
            processRunner.runProcess(*command)
        } catch (e: Exception) {
            gitOperationFailureUseCase.handleGitFailure(
                gitCommand = command.toList(),
                errorOutput = e.message ?: "unknown",
                context = toGitFailureContext(context),
            )
        }
    }

    /**
     * Builds a git command array, prepending `-C <workingDir>` when [workingDir] is set.
     */
    private fun gitCommand(vararg args: String): Array<String> {
        return if (workingDir != null) {
            arrayOf("git", "-C", workingDir.toString(), *args)
        } else {
            arrayOf("git", *args)
        }
    }

    private fun toGitFailureContext(context: SubPartDoneContext): GitFailureContext {
        return GitFailureContext(
            partName = context.partName,
            subPartName = context.subPartName,
            iterationNumber = context.currentIteration,
        )
    }
}
