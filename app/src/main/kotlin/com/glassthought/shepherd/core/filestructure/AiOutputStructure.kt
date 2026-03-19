package com.glassthought.shepherd.core.filestructure

import java.nio.file.Path

/**
 * Pure path-resolution for the `.ai_out/` directory tree.
 *
 * Every element in the schema (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) has a dedicated method
 * returning an absolute [Path]. No I/O is performed — callers are responsible for
 * creating directories and files.
 *
 * Planning sub-parts have **no** part-level grouping (flat under `planning/`).
 * Execution sub-parts are nested under a part directory (`execution/${part}/${subPart}/`).
 * `__feedback/` directories exist only at the execution **part** level.
 */
class AiOutputStructure(
    private val repoRoot: Path,
    private val branch: String,
) {

    // -- branch root --------------------------------------------------------

    fun branchRoot(): Path = repoRoot.resolve(AI_OUT_DIR).resolve(branch)

    // -- harness_private/ ---------------------------------------------------

    fun harnessPrivateDir(): Path = branchRoot().resolve(HARNESS_PRIVATE)

    fun currentStateJson(): Path = harnessPrivateDir().resolve(CURRENT_STATE_JSON)

    fun planFlowJson(): Path = harnessPrivateDir().resolve(PLAN_FLOW_JSON)

    // -- shared/plan/ -------------------------------------------------------

    fun sharedPlanDir(): Path = branchRoot().resolve(SHARED).resolve(PLAN_DIR)

    fun planMd(): Path = sharedPlanDir().resolve(PLAN_MD)

    // -- planning/${subPart}/ -----------------------------------------------

    fun planningSubPartDir(subPartName: String): Path =
        branchRoot().resolve(PLANNING).resolve(subPartName)

    fun planningSubPartPrivateDir(subPartName: String): Path =
        planningSubPartDir(subPartName).resolve(PRIVATE)

    fun planningPrivateMd(subPartName: String): Path =
        planningSubPartPrivateDir(subPartName).resolve(PRIVATE_MD)

    fun planningCommInDir(subPartName: String): Path =
        planningSubPartDir(subPartName).resolve(COMM).resolve(IN)

    fun planningCommOutDir(subPartName: String): Path =
        planningSubPartDir(subPartName).resolve(COMM).resolve(OUT)

    fun planningInstructionsMd(subPartName: String): Path =
        planningCommInDir(subPartName).resolve(INSTRUCTIONS_MD)

    fun planningPublicMd(subPartName: String): Path =
        planningCommOutDir(subPartName).resolve(PUBLIC_MD)

    // -- execution/${part}/ -------------------------------------------------

    fun executionPartDir(partName: String): Path =
        branchRoot().resolve(EXECUTION).resolve(partName)

    fun executionSubPartDir(partName: String, subPartName: String): Path =
        executionPartDir(partName).resolve(subPartName)

    // -- execution/${part}/__feedback/ --------------------------------------

    fun feedbackDir(partName: String): Path =
        executionPartDir(partName).resolve(FEEDBACK)

    fun feedbackPendingDir(partName: String): Path =
        feedbackDir(partName).resolve(PENDING)

    fun feedbackAddressedDir(partName: String): Path =
        feedbackDir(partName).resolve(ADDRESSED)

    fun feedbackRejectedDir(partName: String): Path =
        feedbackDir(partName).resolve(REJECTED)

    // -- execution/${part}/${subPart}/ sub-part internals -------------------

    fun executionSubPartPrivateDir(partName: String, subPartName: String): Path =
        executionSubPartDir(partName, subPartName).resolve(PRIVATE)

    fun executionPrivateMd(partName: String, subPartName: String): Path =
        executionSubPartPrivateDir(partName, subPartName).resolve(PRIVATE_MD)

    fun executionCommInDir(partName: String, subPartName: String): Path =
        executionSubPartDir(partName, subPartName).resolve(COMM).resolve(IN)

    fun executionCommOutDir(partName: String, subPartName: String): Path =
        executionSubPartDir(partName, subPartName).resolve(COMM).resolve(OUT)

    fun executionInstructionsMd(partName: String, subPartName: String): Path =
        executionCommInDir(partName, subPartName).resolve(INSTRUCTIONS_MD)

    fun executionPublicMd(partName: String, subPartName: String): Path =
        executionCommOutDir(partName, subPartName).resolve(PUBLIC_MD)

    companion object {
        private const val AI_OUT_DIR = ".ai_out"
        private const val HARNESS_PRIVATE = "harness_private"
        private const val CURRENT_STATE_JSON = "current_state.json"
        private const val PLAN_FLOW_JSON = "plan_flow.json"
        private const val SHARED = "shared"
        private const val PLAN_DIR = "plan"
        private const val PLAN_MD = "PLAN.md"
        private const val PLANNING = "planning"
        private const val EXECUTION = "execution"
        private const val FEEDBACK = "__feedback"
        private const val PENDING = "pending"
        private const val ADDRESSED = "addressed"
        private const val REJECTED = "rejected"
        private const val PRIVATE = "private"
        private const val PRIVATE_MD = "PRIVATE.md"
        private const val COMM = "comm"
        private const val IN = "in"
        private const val OUT = "out"
        private const val INSTRUCTIONS_MD = "instructions.md"
        private const val PUBLIC_MD = "PUBLIC.md"
    }
}
