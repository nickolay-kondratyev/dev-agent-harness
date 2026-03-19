package com.glassthought.shepherd.core.state

/**
 * Thrown when plan_flow.json validation fails during conversion to execution parts.
 *
 * Contains the specific validation error so that the caller (DetailedPlanningUseCase)
 * can inject it as context for the planner on the next attempt.
 *
 * WHY-NOT AsgardBaseException: not available in current asgardCore 1.0.0 jar.
 * Follow-up ticket to migrate: nid_azwnh5dk5rdhgnd8653hdf6rv_E
 */
class PlanConversionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
