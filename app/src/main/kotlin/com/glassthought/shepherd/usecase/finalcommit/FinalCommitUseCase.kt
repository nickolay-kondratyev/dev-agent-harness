package com.glassthought.shepherd.usecase.finalcommit

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.supporting.git.GitCommandBuilder
import com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase
import java.nio.file.Path

/**
 * Performs a final `git add -A && git commit` to capture any remaining state
 * (e.g., final `CurrentState` flush to `current_state.json`).
 *
 * Skipped if the working tree is clean (no changes since last commit).
 */
fun interface FinalCommitUseCase {
    suspend fun commitIfDirty()

    companion object {
        /**
         * Creates the production [FinalCommitUseCaseImpl].
         *
         * @param outFactory Structured logging factory.
         * @param processRunner Runs CLI commands.
         * @param gitOperationFailureUseCase Handles git failures (index.lock recovery, escalation).
         * @param workingDir Optional git working directory; when null, uses current directory.
         */
        fun standard(
            outFactory: OutFactory,
            processRunner: ProcessRunner,
            gitOperationFailureUseCase: GitOperationFailureUseCase,
            workingDir: Path? = null,
        ): FinalCommitUseCase = FinalCommitUseCaseImpl(
            outFactory = outFactory,
            processRunner = processRunner,
            gitOperationFailureUseCase = gitOperationFailureUseCase,
            gitCommandBuilder = GitCommandBuilder(workingDir),
        )
    }
}
