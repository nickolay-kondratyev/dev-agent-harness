package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import kotlin.time.Duration

/**
 * Result of [AgentUnresponsiveUseCase.handle].
 *
 * - [SessionKilled] — TMUX session was killed; the facade should complete the deferred
 *   with [AgentSignal.Crashed].
 * - [PingSent] — a health ping was sent; the facade should continue its await loop
 *   and re-check after `healthTimeouts.pingResponse`.
 */
sealed class UnresponsiveHandleResult {
    /** Session killed — agent is declared crashed. Contains the [AgentSignal.Crashed] to complete the deferred with. */
    data class SessionKilled(val signal: AgentSignal.Crashed) : UnresponsiveHandleResult()

    /** Ping sent — loop should continue waiting for proof of life. */
    data object PingSent : UnresponsiveHandleResult()
}

/**
 * Kills a single TMUX session. Extracted as a fun interface so
 * [AgentUnresponsiveUseCaseImpl] can be unit-tested without a real [TmuxSessionManager].
 */
fun interface SingleSessionKiller {
    suspend fun killSession(session: TmuxSession)
}

/**
 * Handles an unresponsive agent detected by the health-aware await loop
 * (ref.ap.QCjutDexa2UBDaKB3jTcF.E).
 *
 * Parameterized by [DetectionContext]:
 * - [DetectionContext.STARTUP_TIMEOUT] / [DetectionContext.PING_TIMEOUT] → kill session, return [UnresponsiveHandleResult.SessionKilled].
 * - [DetectionContext.NO_ACTIVITY_TIMEOUT] → send ping, return [UnresponsiveHandleResult.PingSent].
 *
 * See spec at `doc/use-case/HealthMonitoring.md` § AgentUnresponsiveUseCase — DetectionContext.
 */
fun interface AgentUnresponsiveUseCase {

    /**
     * @param detectionContext Why the agent was detected as unresponsive.
     * @param tmuxSession The TMUX session to kill or ping.
     * @param diagnostics Context-specific diagnostic info for structured logging.
     */
    suspend fun handle(
        detectionContext: DetectionContext,
        tmuxSession: TmuxSession,
        diagnostics: UnresponsiveDiagnostics,
    ): UnresponsiveHandleResult
}

/**
 * Diagnostic information passed to [AgentUnresponsiveUseCase.handle] for structured logging.
 *
 * Not all fields are relevant for every [DetectionContext]:
 * - [handshakeGuid] is primarily logged for [DetectionContext.STARTUP_TIMEOUT].
 * - [staleDuration] is logged for [DetectionContext.NO_ACTIVITY_TIMEOUT] and [DetectionContext.PING_TIMEOUT].
 * - [timeoutDuration] is logged for all contexts (the threshold that was exceeded).
 */
data class UnresponsiveDiagnostics(
    /** The handshake GUID identifying this agent session. */
    val handshakeGuid: HandshakeGuid,
    /** The timeout threshold that was exceeded. */
    val timeoutDuration: Duration,
    /** How long since the last activity (stale duration). */
    val staleDuration: Duration,
)

/**
 * Default implementation of [AgentUnresponsiveUseCase].
 *
 * - [DetectionContext.STARTUP_TIMEOUT] and [DetectionContext.PING_TIMEOUT]: logs detection,
 *   kills the TMUX session via [sessionKiller], returns [UnresponsiveHandleResult.SessionKilled].
 * - [DetectionContext.NO_ACTIVITY_TIMEOUT]: logs detection, sends a health ping via
 *   [TmuxSession.sendRawKeys], returns [UnresponsiveHandleResult.PingSent].
 *
 * Stateless — all state comes from method parameters.
 */
class AgentUnresponsiveUseCaseImpl(
    outFactory: OutFactory,
    private val sessionKiller: SingleSessionKiller,
) : AgentUnresponsiveUseCase {

    private val out = outFactory.getOutForClass(AgentUnresponsiveUseCaseImpl::class)

    override suspend fun handle(
        detectionContext: DetectionContext,
        tmuxSession: TmuxSession,
        diagnostics: UnresponsiveDiagnostics,
    ): UnresponsiveHandleResult {
        logDetection(detectionContext, tmuxSession, diagnostics)

        return when (detectionContext) {
            DetectionContext.STARTUP_TIMEOUT,
            DetectionContext.PING_TIMEOUT -> killSessionAndReturnCrashed(detectionContext, tmuxSession, diagnostics)

            DetectionContext.NO_ACTIVITY_TIMEOUT -> sendPing(tmuxSession)
        }
    }

    private suspend fun killSessionAndReturnCrashed(
        detectionContext: DetectionContext,
        tmuxSession: TmuxSession,
        diagnostics: UnresponsiveDiagnostics,
    ): UnresponsiveHandleResult.SessionKilled {
        sessionKiller.killSession(tmuxSession)

        val details = "Agent unresponsive: context=${detectionContext.name}, " +
            "session=${tmuxSession.name.sessionName}, " +
            "handshakeGuid=${diagnostics.handshakeGuid}, " +
            "staleDuration=${diagnostics.staleDuration}, " +
            "timeoutDuration=${diagnostics.timeoutDuration}"

        return UnresponsiveHandleResult.SessionKilled(
            signal = AgentSignal.Crashed(details),
        )
    }

    private suspend fun sendPing(
        tmuxSession: TmuxSession,
    ): UnresponsiveHandleResult.PingSent {
        out.info(
            "sending_health_ping",
            Val(tmuxSession.name.sessionName, ValType.STRING_USER_AGNOSTIC),
        )

        // Health ping: send Enter via sendRawKeys to provoke an ack-payload response.
        // When AckedPayloadSender is implemented (ref.ap.tbtBcVN2iCl1xfHJthllP.E),
        // this will be replaced with a proper wrapped payload + ACK tracking.
        tmuxSession.sendRawKeys("Enter")

        return UnresponsiveHandleResult.PingSent
    }

    private suspend fun logDetection(
        detectionContext: DetectionContext,
        tmuxSession: TmuxSession,
        diagnostics: UnresponsiveDiagnostics,
    ) {
        when (detectionContext) {
            DetectionContext.STARTUP_TIMEOUT -> out.info(
                "startup_timeout_detected",
                Val(tmuxSession.name.sessionName, ValType.STRING_USER_AGNOSTIC),
                Val(diagnostics.handshakeGuid.value, ValType.STRING_USER_AGNOSTIC),
                Val(diagnostics.timeoutDuration.toString(), ValType.STRING_USER_AGNOSTIC),
            )

            DetectionContext.NO_ACTIVITY_TIMEOUT -> out.info(
                "no_activity_timeout_detected",
                Val(tmuxSession.name.sessionName, ValType.STRING_USER_AGNOSTIC),
                Val(diagnostics.staleDuration.toString(), ValType.STRING_USER_AGNOSTIC),
                Val(diagnostics.timeoutDuration.toString(), ValType.STRING_USER_AGNOSTIC),
            )

            DetectionContext.PING_TIMEOUT -> out.info(
                "ping_timeout_detected",
                Val(tmuxSession.name.sessionName, ValType.STRING_USER_AGNOSTIC),
                Val(diagnostics.staleDuration.toString(), ValType.STRING_USER_AGNOSTIC),
                Val(diagnostics.timeoutDuration.toString(), ValType.STRING_USER_AGNOSTIC),
            )
        }
    }
}
