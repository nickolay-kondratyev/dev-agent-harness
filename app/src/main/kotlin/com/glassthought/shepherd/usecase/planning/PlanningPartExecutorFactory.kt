package com.glassthought.shepherd.usecase.planning

import com.glassthought.shepherd.core.executor.PartExecutor

/**
 * Factory that creates a [PartExecutor] configured for the planning phase
 * (PLANNER doer + PLAN_REVIEWER reviewer).
 *
 * Keeps [DetailedPlanningUseCaseImpl] testable — tests can supply a fake factory
 * that returns a pre-configured executor.
 *
 * @param priorConversionErrors validation errors from previous plan conversion attempts.
 *   Implementations should inject these into the planner's context so the agent can
 *   avoid repeating the same mistakes. Empty on the first attempt.
 */
interface PlanningPartExecutorFactory {
    fun create(priorConversionErrors: List<String> = emptyList()): PartExecutor
}
