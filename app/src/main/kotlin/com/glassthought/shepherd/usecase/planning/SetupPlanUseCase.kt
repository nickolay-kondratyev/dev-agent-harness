package com.glassthought.shepherd.usecase.planning

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.workflow.WorkflowDefinition

/**
 * Routes to the appropriate planning strategy based on workflow type and returns `List<Part>`.
 *
 * The caller receives execution-ready parts regardless of workflow mode.
 *
 * See spec: ref.ap.VLjh11HdzC8ZOhNCDOr2g.E
 */
@AnchorPoint("ap.VLjh11HdzC8ZOhNCDOr2g.E")
fun interface SetupPlanUseCase {
    suspend fun setup(): List<Part>
}

/**
 * Routes to the correct planning strategy based on [WorkflowDefinition]:
 * - **straightforward** -> [straightforwardPlanUseCase] (static parts from workflow JSON)
 * - **with-planning** -> [detailedPlanningUseCase] (runs planning loop, returns execution parts)
 *
 * The `else` branch is unreachable due to [WorkflowDefinition] init validation
 * (exactly one of `parts` or `planningParts` must be set), but we throw
 * [IllegalStateException] for safety.
 */
class SetupPlanUseCaseImpl(
    private val workflowDefinition: WorkflowDefinition,
    private val straightforwardPlanUseCase: StraightforwardPlanUseCase,
    private val detailedPlanningUseCase: DetailedPlanningUseCase,
    private val outFactory: OutFactory,
) : SetupPlanUseCase {

    private val out = outFactory.getOutForClass(SetupPlanUseCaseImpl::class)

    override suspend fun setup(): List<Part> {
        return when {
            workflowDefinition.isStraightforward -> {
                out.info("routing_to_straightforward_plan")
                straightforwardPlanUseCase.execute()
            }
            workflowDefinition.isWithPlanning -> {
                out.info("routing_to_detailed_planning")
                detailedPlanningUseCase.execute()
            }
            else -> {
                // WHY: WorkflowDefinition init block enforces exactly one of parts/planningParts,
                // so this branch is unreachable in practice. Kept as a safety net.
                error(
                    "WorkflowDefinition is neither straightforward nor with-planning — " +
                        "this should be unreachable due to init validation."
                )
            }
        }
    }
}
