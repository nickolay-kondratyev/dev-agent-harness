package com.glassthought.chainsaw.core.supporting.git

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import java.nio.file.Path

/**
 * Manages git branch operations: creating branches and querying the current branch.
 *
 * Uses the git CLI via [ProcessRunner]. Fails fast on git errors (non-zero exit codes
 * propagate as exceptions from [ProcessRunner.runProcess]).
 */
interface GitBranchManager {

    /**
     * Creates a new branch and checks it out.
     *
     * @param branchName The name for the new branch (must not be blank).
     * @throws IllegalArgumentException if [branchName] is blank.
     * @throws RuntimeException if git fails (e.g., branch already exists).
     */
    suspend fun createAndCheckout(branchName: String)

    /**
     * Returns the name of the currently checked-out branch.
     *
     * @return The current branch name (trimmed).
     * @throws RuntimeException if git fails.
     */
    suspend fun getCurrentBranch(): String

    companion object {
        fun standard(
            outFactory: OutFactory,
            processRunner: ProcessRunner,
            workingDir: Path? = null,
        ): GitBranchManager = GitBranchManagerImpl(outFactory, processRunner, workingDir)
    }
}

/**
 * Default implementation of [GitBranchManager].
 *
 * Delegates git CLI commands to [ProcessRunner]. When [workingDir] is provided,
 * all git commands are prefixed with `-C <dir>` to target a specific repository.
 */
class GitBranchManagerImpl(
    outFactory: OutFactory,
    private val processRunner: ProcessRunner,
    private val workingDir: Path? = null,
) : GitBranchManager {

    private val out = outFactory.getOutForClass(GitBranchManagerImpl::class)

    override suspend fun createAndCheckout(branchName: String) {
        require(branchName.isNotBlank()) { "branchName must not be blank" }

        out.info(
            "creating_and_checking_out_branch",
            Val(branchName, ValType.GIT_BRANCH_NAME),
        )

        processRunner.runProcess(*gitCommand("checkout", "-b", branchName))

        out.info(
            "branch_created_and_checked_out",
            Val(branchName, ValType.GIT_BRANCH_NAME),
        )
    }

    override suspend fun getCurrentBranch(): String {
        out.debug("getting_current_branch") {
            emptyList()
        }

        val output = processRunner.runProcess(*gitCommand("rev-parse", "--abbrev-ref", "HEAD"))
        val branchName = output.trim()

        out.info(
            "current_branch_resolved",
            Val(branchName, ValType.GIT_BRANCH_NAME),
        )

        return branchName
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
}
