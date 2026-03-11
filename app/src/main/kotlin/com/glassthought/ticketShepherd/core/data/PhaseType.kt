package com.glassthought.ticketShepherd.core.data

/**
 * Identifies the workflow phase in which an agent operates.
 *
 * Drives phase-specific configuration (future) and session naming for debuggability.
 */
enum class PhaseType {
    IMPLEMENTOR,
    REVIEWER,
    PLANNER,
    PLAN_REVIEWER,
}
