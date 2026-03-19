package com.glassthought.shepherd.core.agent.contextwindow

/**
 * Thrown when the context window state file cannot be read — either because it is missing
 * (external hook not configured) or because it is malformed.
 *
 * WHY-NOT AsgardBaseException: not available in current asgardCore 1.0.0 jar.
 * Follows same pattern as [com.glassthought.shepherd.core.state.PlanConversionException].
 */
class ContextWindowStateUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
