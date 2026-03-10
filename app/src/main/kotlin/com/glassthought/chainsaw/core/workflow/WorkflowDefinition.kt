package com.glassthought.chainsaw.core.workflow

/**
 * Parsed representation of a workflow definition JSON file.
 *
 * A workflow is either **straightforward** (has [parts]) or **with-planning**
 * (has [planningPhases], [planningIteration], and [executionPhasesFrom]).
 * These two modes are mutually exclusive — exactly one set of fields will be non-null.
 *
 * See design doc: ref.ap.mmcagXtg6ulznKYYNKlNP.E
 *
 * ap.MyWV0mG6ZU8XaQOyo14l4.E
 */
data class WorkflowDefinition(
    val name: String,
    val parts: List<Part>?,
    val planningPhases: List<Phase>?,
    val planningIteration: IterationConfig?,
    val executionPhasesFrom: String?,
)

/**
 * A named unit of work within a straightforward workflow.
 *
 * Contains an ordered list of [phases] that are executed sequentially,
 * with an [iteration] config controlling how many review cycles are allowed.
 */
data class Part(
    val name: String,
    val description: String,
    val phases: List<Phase>,
    val iteration: IterationConfig,
)

/**
 * A single phase within a part or planning sequence.
 *
 * [role] identifies the agent role from the role catalog.
 */
data class Phase(
    val role: String,
)

/**
 * Configuration for iteration limits.
 *
 * [max] is the maximum number of iteration cycles allowed.
 */
data class IterationConfig(
    val max: Int,
)
