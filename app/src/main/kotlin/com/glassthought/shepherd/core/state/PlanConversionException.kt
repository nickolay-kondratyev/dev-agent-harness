package com.glassthought.shepherd.core.state

/**
 * Thrown when plan_flow.json validation fails during conversion to execution parts.
 *
 * Contains the specific validation error so that the caller (DetailedPlanningUseCase)
 * can inject it as context for the planner on the next attempt.
 */
class PlanConversionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
