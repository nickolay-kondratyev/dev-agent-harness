package com.glassthought.shepherd.core.workflow

import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase

/**
 * Static workflow definition parsed from `config/workflows/<name>.json`.
 *
 * A workflow is either **straightforward** (has [parts]) or **with-planning**
 * (has [planningParts] + [executionPhasesFrom]). These are mutually exclusive.
 *
 * - **Straightforward**: [parts] contains the full execution plan; all parts must have [Phase.EXECUTION].
 * - **With-planning**: [planningParts] defines the planning loop (PLANNER / PLAN_REVIEWER);
 *   all parts must have [Phase.PLANNING]. [executionPhasesFrom] names the file the planner
 *   generates (e.g., `"plan_flow.json"`) in `harness_private/`.
 *
 * ap.b4i1YCm3AvwEySAjDRoJg.E
 */
data class WorkflowDefinition(
    val name: String,
    val parts: List<Part>? = null,
    val planningParts: List<Part>? = null,
    val executionPhasesFrom: String? = null,
) {
    /** True when this workflow uses a planning phase before execution. */
    val isWithPlanning: Boolean get() = planningParts != null

    /** True when this workflow has static execution parts (no planning phase). */
    val isStraightforward: Boolean get() = parts != null

    init {
        require((parts != null) xor (planningParts != null)) {
            "WorkflowDefinition must have exactly one of 'parts' (straightforward) " +
                "or 'planningParts' (with-planning), but not both or neither."
        }

        if (planningParts != null) {
            requireNotNull(executionPhasesFrom) {
                "With-planning workflow must specify 'executionPhasesFrom' " +
                    "to name the file the planner generates."
            }
        }
    }
}
