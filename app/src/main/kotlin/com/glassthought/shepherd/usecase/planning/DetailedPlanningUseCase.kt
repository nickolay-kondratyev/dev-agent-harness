package com.glassthought.shepherd.usecase.planning

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.PlanConversionException
import com.glassthought.shepherd.core.state.PlanFlowConverter
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase

/**
 * Owns the full planning lifecycle for `with-planning` workflows.
 *
 * Creates a planning executor, runs it, converts the approved plan to execution parts,
 * and returns `List<Part>`. The caller receives execution-ready parts with no knowledge
 * of the two-phase protocol.
 *
 * See spec: ref.ap.cJhuVZTkwfrWUzTmaMbR3.E
 */
@AnchorPoint("ap.xK7mNqR9wVpL2jZfYc5Td.E")
fun interface DetailedPlanningUseCase {
    suspend fun execute(): List<Part>
}

/**
 * Default implementation of [DetailedPlanningUseCase].
 *
 * Flow:
 * 1. Create planning [PartExecutor] via [partExecutorFactory]
 * 2. Run executor → [PartResult]
 * 3. On failure → delegate to [failedToExecutePlanUseCase] (exits process)
 * 4. On success → call [planFlowConverter.convertAndAppend] to transform approved plan
 * 5. On [PlanConversionException] → retry the full planning loop (counts against [maxConversionRetries])
 * 6. Return execution parts
 *
 * **Session cleanup**: [PartExecutorImpl] kills all sessions internally on completion,
 * so this class does NOT call removeAllForPart separately.
 */
class DetailedPlanningUseCaseImpl(
    private val partExecutorFactory: PlanningPartExecutorFactory,
    private val planFlowConverter: PlanFlowConverter,
    private val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
    private val currentState: CurrentState,
    private val maxConversionRetries: Int,
    private val outFactory: OutFactory,
) : DetailedPlanningUseCase {

    private val out = outFactory.getOutForClass(DetailedPlanningUseCaseImpl::class)

    override suspend fun execute(): List<Part> {
        val conversionErrors = mutableListOf<String>()
        var remainingRetries = maxConversionRetries

        while (true) {
            val executor = partExecutorFactory.create(conversionErrors.toList())
            val result = executor.execute()

            when (result) {
                is PartResult.Completed -> {
                    // Planning executor succeeded — proceed to plan conversion
                }
                is PartResult.FailedWorkflow,
                is PartResult.FailedToConverge,
                is PartResult.AgentCrashed -> {
                    failedToExecutePlanUseCase.handleFailure(result)
                    // handleFailure returns Nothing — unreachable
                }
            }

            return try {
                val executionParts = planFlowConverter.convertAndAppend(currentState)
                out.info("plan_conversion_succeeded") {
                    listOf(Val(executionParts.size, ValType.COUNT))
                }
                executionParts
            } catch (e: PlanConversionException) {
                conversionErrors.add(e.message ?: "unknown conversion error")
                remainingRetries--
                out.warn(
                    "plan_conversion_failed_will_retry",
                    Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
                    Val(remainingRetries.toString(), ShepherdValType.ATTEMPT_NUMBER),
                )

                if (remainingRetries <= 0) {
                    failedToExecutePlanUseCase.handleFailure(
                        PartResult.FailedToConverge(
                            "Plan conversion failed after ${maxConversionRetries} retries: ${e.message}"
                        )
                    )
                    // handleFailure returns Nothing — unreachable
                }
                // Loop back to re-run the planning executor
                continue
            }
        }
    }
}
