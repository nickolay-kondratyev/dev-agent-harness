package com.glassthought.shepherd.usecase.spawn

import kotlin.time.Duration

/**
 * Thrown when TMUX session creation fails (non-zero exit from `tmux new-session`).
 *
 * The caller maps this to `AgentSignal.Crashed` — no health monitoring loop is entered
 * because there is no session to monitor.
 *
 * @property sessionName The TMUX session name that failed to create.
 */
class TmuxSessionCreationException(
    val sessionName: String,
    cause: Throwable,
) : RuntimeException(
    "Failed to create TMUX session [$sessionName]",
    cause,
)

/**
 * Thrown when the agent does not call `/callback-shepherd/signal/started` within the
 * configured startup timeout.
 *
 * The caller maps this to `AgentSignal.Crashed` via `AgentUnresponsiveUseCase` with
 * `DetectionContext.STARTUP_TIMEOUT`.
 *
 * @property sessionName The TMUX session name that timed out during startup.
 * @property timeout The startup timeout duration that was exceeded.
 */
class StartupTimeoutException(
    val sessionName: String,
    val timeout: Duration,
    cause: Throwable? = null,
) : RuntimeException(
    "Agent startup timed out after [$timeout] for TMUX session [$sessionName]",
    cause,
)
