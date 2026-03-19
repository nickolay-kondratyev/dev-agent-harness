package com.glassthought.shepherd.usecase.planning

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.workflow.WorkflowDefinition

/**
 * Returns the static execution parts from a straightforward workflow definition.
 *
 * For straightforward workflows, the parts are predefined in the workflow JSON —
 * no planning phase is needed.
 *
 * See spec: ref.ap.6iySKY6iakspLNi3WenRO.E
 */
@AnchorPoint("ap.6iySKY6iakspLNi3WenRO.E")
fun interface StraightforwardPlanUseCase {
    suspend fun execute(): List<Part>
}

class StraightforwardPlanUseCaseImpl(
    private val workflowDefinition: WorkflowDefinition,
    private val outFactory: OutFactory,
) : StraightforwardPlanUseCase {

    private val out = outFactory.getOutForClass(StraightforwardPlanUseCaseImpl::class)

    override suspend fun execute(): List<Part> {
        out.info("returning_static_parts_from_straightforward_workflow")
        return workflowDefinition.parts!!
    }
}
