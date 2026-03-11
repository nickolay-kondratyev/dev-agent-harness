package com.glassthought.chainsaw.core.filestructure

import com.asgard.core.annotation.AnchorPoint
import java.nio.file.Files
import java.nio.file.Path

/**
 * A part within a workflow, consisting of a name and the sub-parts that execute within it.
 *
 * Co-located with [AiOutputStructure] as it is a simple value type tightly coupled to
 * the [AiOutputStructure.ensureStructure] API.
 */
data class Part(val name: String, val subParts: List<String>)

/**
 * Manages path resolution and directory creation for the `.ai_out/${branch}/` directory tree
 * used by the Chainsaw agent harness.
 *
 * All path resolution methods are pure (no I/O) -- they compute and return [Path] instances.
 * Only [ensureStructure] performs I/O by creating directories.
 *
 * ref.ap.BXQlLDTec7cVVOrzXWfR7.E — See "File Structure"
 *
 * @param repoRoot the root directory of the git repository.
 * @throws IllegalArgumentException if [repoRoot] is not an existing directory on the filesystem.
 */
@AnchorPoint("ap.XBNUQHLjDLpAr8F9IOyXU.E")
class AiOutputStructure(
    private val repoRoot: Path,
) {
    init {
        require(Files.isDirectory(repoRoot)) {
            "Repository root is not a directory: [${repoRoot}]"
        }
    }

    companion object {
        private const val AI_OUT_DIR = ".ai_out"
        private const val HARNESS_PRIVATE_DIR = "harness_private"
        private const val SHARED_DIR = "shared"
        private const val PLAN_DIR = "plan"
        private const val PLANNING_DIR = "planning"
        private const val PHASES_DIR = "phases"
        private const val SESSION_IDS_DIR = "session_ids"
        private const val PUBLIC_MD = "PUBLIC.md"
        private const val SHARED_CONTEXT_MD = "SHARED_CONTEXT.md"
    }

    // -- Path resolution (pure, no I/O) --

    private fun requireNotBlank(value: String, paramName: String) {
        require(value.isNotBlank()) { "$paramName must not be blank" }
    }

    private fun branchRoot(branch: String): Path {
        requireNotBlank(branch, "branch")
        return repoRoot.resolve(AI_OUT_DIR).resolve(branch)
    }

    fun harnessPrivateDir(branch: String): Path =
        branchRoot(branch).resolve(HARNESS_PRIVATE_DIR)

    fun sharedDir(branch: String): Path =
        branchRoot(branch).resolve(SHARED_DIR)

    fun planDir(branch: String): Path =
        sharedDir(branch).resolve(PLAN_DIR)

    fun planningSubPartDir(branch: String, subPart: String): Path {
        requireNotBlank(subPart, "subPart")
        return branchRoot(branch).resolve(PLANNING_DIR).resolve(subPart)
    }

    fun planningPublicMd(branch: String, subPart: String): Path =
        planningSubPartDir(branch, subPart).resolve(PUBLIC_MD)

    fun planningSessionIdsDir(branch: String, subPart: String): Path =
        planningSubPartDir(branch, subPart).resolve(SESSION_IDS_DIR)

    fun subPartDir(branch: String, part: String, subPart: String): Path {
        requireNotBlank(part, "part")
        requireNotBlank(subPart, "subPart")
        return branchRoot(branch).resolve(PHASES_DIR).resolve(part).resolve(subPart)
    }

    fun sessionIdsDir(branch: String, part: String, subPart: String): Path =
        subPartDir(branch, part, subPart).resolve(SESSION_IDS_DIR)

    fun publicMd(branch: String, part: String, subPart: String): Path =
        subPartDir(branch, part, subPart).resolve(PUBLIC_MD)

    fun sharedContextMd(branch: String): Path =
        sharedDir(branch).resolve(SHARED_CONTEXT_MD)

    // -- Directory creation (I/O) --

    /**
     * Creates the full directory tree for a given branch and its parts.
     *
     * This method is idempotent -- calling it multiple times with the same arguments is safe.
     *
     * @param branch the git branch name.
     * @param parts the list of [Part]s defining the workflow phases and their sub-parts.
     * @param planningSubParts sub-parts participating in the planning loop (e.g., "1_plan", "2_plan_review").
     *        When non-empty, creates `planning/${subPart}/` and `planning/${subPart}/session_ids/` directories.
     */
    fun ensureStructure(
        branch: String,
        parts: List<Part>,
        planningSubParts: List<String> = emptyList(),
    ) {
        Files.createDirectories(harnessPrivateDir(branch))
        Files.createDirectories(planDir(branch))

        for (subPart in planningSubParts) {
            Files.createDirectories(planningSubPartDir(branch, subPart))
            Files.createDirectories(planningSessionIdsDir(branch, subPart))
        }

        for (part in parts) {
            for (subPart in part.subParts) {
                Files.createDirectories(subPartDir(branch, part.name, subPart))
                Files.createDirectories(sessionIdsDir(branch, part.name, subPart))
            }
        }
    }
}
