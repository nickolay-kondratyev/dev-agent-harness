package com.glassthought.shepherd.usecase.spawn

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.exception.base.AsgardBaseException
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
) : AsgardBaseException(
    "failed_to_create_tmux_session",
    cause,
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
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
) : AsgardBaseException(
    "agent_startup_timed_out",
    cause,
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(timeout.toString(), ValType.STRING_USER_AGNOSTIC),
)
