package com.glassthought.shepherd.usecase.finalcommit

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.supporting.git.GitCommandBuilder
import com.glassthought.shepherd.core.supporting.git.GitFailureContext
import com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase
import com.glassthought.shepherd.core.supporting.git.GitStagingCommitHelper

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
    processRunner: ProcessRunner,
    gitOperationFailureUseCase: GitOperationFailureUseCase,
    gitCommandBuilder: GitCommandBuilder = GitCommandBuilder(),
) : FinalCommitUseCase {

    private val out = outFactory.getOutForClass(FinalCommitUseCaseImpl::class)

    private val gitStagingCommitHelper = GitStagingCommitHelper(
        processRunner = processRunner,
        gitOperationFailureUseCase = gitOperationFailureUseCase,
        gitCommandBuilder = gitCommandBuilder,
    )

    override suspend fun commitIfDirty() {
        out.info("final_commit_triggered")

        gitStagingCommitHelper.stageAll(FAILURE_CONTEXT)

        if (!gitStagingCommitHelper.hasStagedChanges()) {
            out.info("no_staged_changes_skipping_final_commit")
            return
        }

        gitStagingCommitHelper.commit("commit", "-m", COMMIT_MESSAGE, failureContext = FAILURE_CONTEXT)

        out.info("final_commit_created")
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
