package com.glassthought.shepherd.core.state

import com.glassthought.shepherd.core.workflow.WorkflowDefinition

/**
 * Creates the initial in-memory [CurrentState] from a [WorkflowDefinition].
 *
 * **Straightforward** workflows produce parts with [Phase.EXECUTION] and runtime fields
 * (`status = NOT_STARTED`, `iteration.current = 0` for reviewers).
 *
 * **With-planning** workflows produce a single planning part from [WorkflowDefinition.planningParts]
 * with the same runtime field initialization. Execution parts are added later via
 * [CurrentState.appendExecutionParts] after planning converges.
 *
 * ap.Chdyvp7XQhz5cTxffqFCf.E
 */
fun interface CurrentStateInitializer {
    fun createInitialState(workflowDefinition: WorkflowDefinition): CurrentState
}

class CurrentStateInitializerImpl : CurrentStateInitializer {

    override fun createInitialState(workflowDefinition: WorkflowDefinition): CurrentState {
        val sourceParts = when {
            workflowDefinition.isStraightforward -> workflowDefinition.parts!!
            workflowDefinition.isWithPlanning -> workflowDefinition.planningParts!!
            // WorkflowDefinition.init enforces exactly one of parts/planningParts is non-null
            else -> error("WorkflowDefinition must have either parts or planningParts")
        }

        val initializedParts = sourceParts.map { part -> initializePart(part) }

        return CurrentState(parts = initializedParts.toMutableList())
    }

    companion object {
        /**
         * Adds runtime fields to a [Part]'s sub-parts:
         * - `status = NOT_STARTED` on every sub-part
         * - `iteration.current = 0` on sub-parts that have an iteration config (reviewers)
         */
        fun initializePart(part: Part): Part {
            return part.copy(
                subParts = part.subParts.map { subPart -> initializeSubPart(subPart) }
            )
        }

        private fun initializeSubPart(subPart: SubPart): SubPart {
            return subPart.copy(
                status = SubPartStatus.NOT_STARTED,
                iteration = subPart.iteration?.copy(current = 0),
                sessionIds = null, // Clear any stale session records from plan_flow.json
            )
        }
    }
}
