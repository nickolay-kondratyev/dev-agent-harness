package com.glassthought.shepherd.usecase.planning

import com.glassthought.shepherd.core.executor.PartExecutor

/**
 * Factory that creates a [PartExecutor] configured for the planning phase
 * (PLANNER doer + PLAN_REVIEWER reviewer).
 *
 * Keeps [DetailedPlanningUseCaseImpl] testable — tests can supply a fake factory
 * that returns a pre-configured executor.
 */
fun interface PlanningPartExecutorFactory {
    fun create(): PartExecutor
}
