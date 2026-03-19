package com.glassthought.shepherd.core.supporting.git

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import java.nio.file.Path

/**
 * Validates that the git working tree is clean before any git operations.
 *
 * This is a startup guard (ref.ap.QL051Wl21jmmYqTQTLglf.E) invoked by TicketShepherdCreator
 * before branch creation to prevent mixing pre-existing uncommitted work with agent output.
 */
interface WorkingTreeValidator {

    /**
     * Validates the working tree is clean.
     *
     * @throws IllegalStateException if the working tree contains uncommitted or untracked changes.
     */
    suspend fun validate()

    companion object {
        fun standard(
            outFactory: OutFactory,
            processRunner: ProcessRunner,
            workingDir: Path? = null,
        ): WorkingTreeValidator = WorkingTreeValidatorImpl(
            outFactory = outFactory,
            processRunner = processRunner,
            gitCommandBuilder = GitCommandBuilder(workingDir),
        )
    }
}

/**
 * Default implementation of [WorkingTreeValidator].
 *
 * Runs `git status --porcelain` and fails hard when output is non-empty,
 * listing dirty files and instructing the user to commit or stash.
 */
class WorkingTreeValidatorImpl(
    outFactory: OutFactory,
    private val processRunner: ProcessRunner,
    private val gitCommandBuilder: GitCommandBuilder,
) : WorkingTreeValidator {

    private val out = outFactory.getOutForClass(WorkingTreeValidatorImpl::class)

    override suspend fun validate() {
        out.debug("validating_working_tree_is_clean") {
            emptyList()
        }

        val porcelainOutput = processRunner.runProcess(*gitCommandBuilder.build("status", "--porcelain"))

        if (porcelainOutput.trim().isEmpty()) {
            out.info("working_tree_is_clean")
            return
        }

        out.warn(
            "working_tree_is_dirty",
            Val(porcelainOutput.trim(), ValType.STRING_USER_AGNOSTIC),
        )

        throw IllegalStateException(
            buildErrorMessage(porcelainOutput.trim())
        )
    }

    companion object {
        // WHY: Extracted to companion for testability of the message format without needing ProcessRunner.
        internal fun buildErrorMessage(dirtyFiles: String): String {
            return """
                |ERROR: Working tree is not clean. Shepherd requires a clean working tree to avoid
                |mixing pre-existing uncommitted work with agent output.
                |
                |Dirty files:
                |${dirtyFiles.lines().joinToString("\n") { "  $it" }}
                |
                |Please commit or stash your changes before running 'shepherd run'.
            """.trimMargin()
        }
    }
}
