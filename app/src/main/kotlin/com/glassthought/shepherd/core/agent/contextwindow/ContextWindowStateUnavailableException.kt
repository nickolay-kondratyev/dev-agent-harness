package com.glassthought.shepherd.core.agent.contextwindow

import com.asgard.core.exception.base.AsgardBaseException

/**
 * Thrown when the context window state file cannot be read — either because it is missing
 * (external hook not configured) or because it is malformed.
 */
class ContextWindowStateUnavailableException(
    message: String,
    cause: Throwable? = null,
) : AsgardBaseException(message, cause)
