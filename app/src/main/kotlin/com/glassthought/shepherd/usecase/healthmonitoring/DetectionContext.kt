package com.glassthought.shepherd.usecase.healthmonitoring

/**
 * Classifies the reason an agent was detected as unresponsive.
 *
 * Parameterizes [AgentUnresponsiveUseCase] — each context carries different diagnostic
 * information and may trigger a different action (ping vs kill).
 *
 * See spec at `doc/use-case/HealthMonitoring.md` § AgentUnresponsiveUseCase — DetectionContext.
 */
enum class DetectionContext {
    /** No callback of any kind within `healthTimeouts.startup` after agent spawn. */
    STARTUP_TIMEOUT,

    /** `lastActivityTimestamp` stale beyond `healthTimeouts.normalActivity` — sends a ping, does NOT crash. */
    NO_ACTIVITY_TIMEOUT,

    /** No callback after `healthTimeouts.pingResponse` window following a health ping. */
    PING_TIMEOUT,
}
