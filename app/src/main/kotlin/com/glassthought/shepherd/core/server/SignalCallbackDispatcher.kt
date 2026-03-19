package com.glassthought.shepherd.core.server

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import com.glassthought.shepherd.core.session.SessionsState
import java.time.Clock

/**
 * Maps signal callback data (action name + JSON payload fields) to [AgentSignal],
 * performs session lookup, completes the signal deferred, and updates lastActivityTimestamp.
 *
 * This is the layer between HTTP routing and the session registry ([SessionsState]).
 * When the HTTP server receives a POST to `/callback-shepherd/signal/{action}`,
 * it parses the JSON and delegates to [dispatch].
 *
 * See ref.ap.wLpW8YbvqpRdxDplnN7Vh.E for the agent-to-server protocol spec.
 */
@AnchorPoint("ap.olc7abIAv3YNk3PEE92SY.E")
class SignalCallbackDispatcher(
    private val sessionsState: SessionsState,
    outFactory: OutFactory,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val out = outFactory.getOutForClass(SignalCallbackDispatcher::class)

    /**
     * Dispatches a signal callback to the appropriate session.
     *
     * @param action The signal action name from the URL path (e.g., "done", "self-compacted").
     * @param payload Parsed fields from the JSON body. Expected keys vary by action:
     *   - `"done"`: `handshakeGuid`, `result`
     *   - `"fail-workflow"`: `handshakeGuid`, `reason`
     *   - `"self-compacted"`: `handshakeGuid`
     * @return [DispatchResult] indicating success, session not found, or bad request.
     */
    suspend fun dispatch(action: String, payload: Map<String, String>): DispatchResult {
        val guidValue = payload[FIELD_HANDSHAKE_GUID]
        if (guidValue == null) {
            return DispatchResult.BadRequest("missing_field: $FIELD_HANDSHAKE_GUID")
        }

        val signalOrError = mapActionToSignal(action, payload)

        return when (signalOrError) {
            is DispatchResult.BadRequest -> signalOrError
            is DispatchResult.Success -> completeSession(
                guid = HandshakeGuid(guidValue),
                agentSignal = signalOrError.signal,
                action = action,
                guidValue = guidValue,
            )
            is DispatchResult.SessionNotFound -> signalOrError
        }
    }

    private fun mapActionToSignal(action: String, payload: Map<String, String>): DispatchResult {
        return when (action) {
            ProtocolVocabulary.Signal.DONE -> mapDoneSignal(payload)
            ProtocolVocabulary.Signal.FAIL_WORKFLOW -> mapFailWorkflowSignal(payload)
            ProtocolVocabulary.Signal.SELF_COMPACTED -> DispatchResult.Success(AgentSignal.SelfCompacted)
            else -> DispatchResult.BadRequest("unknown_action: $action")
        }
    }

    private suspend fun completeSession(
        guid: HandshakeGuid,
        agentSignal: AgentSignal,
        action: String,
        guidValue: String,
    ): DispatchResult {
        val sessionEntry = sessionsState.lookup(guid)
        if (sessionEntry == null) {
            out.warn(
                "signal_dispatch_session_not_found",
                Val(guidValue, ShepherdValType.HANDSHAKE_GUID),
            )
            return DispatchResult.SessionNotFound(guid)
        }

        sessionEntry.lastActivityTimestamp.set(clock.instant())
        val wasCompleted = sessionEntry.signalDeferred.complete(agentSignal)

        if (!wasCompleted) {
            out.warn(
                "signal_dispatch_duplicate_callback",
                Val(action, ShepherdValType.SIGNAL_ACTION),
                Val(guidValue, ShepherdValType.HANDSHAKE_GUID),
            )
        }

        out.info(
            "signal_dispatched",
            Val(action, ShepherdValType.SIGNAL_ACTION),
            Val(guidValue, ShepherdValType.HANDSHAKE_GUID),
        )

        return DispatchResult.Success(agentSignal)
    }

    private fun mapDoneSignal(payload: Map<String, String>): DispatchResult {
        val resultString = payload[FIELD_RESULT]
            ?: return DispatchResult.BadRequest("missing_field: $FIELD_RESULT")

        return DONE_RESULT_MAP[resultString]
            ?.let { DispatchResult.Success(AgentSignal.Done(it)) }
            ?: DispatchResult.BadRequest("invalid_done_result: $resultString")
    }

    private fun mapFailWorkflowSignal(payload: Map<String, String>): DispatchResult {
        val reason = payload[FIELD_REASON]
        if (reason == null) {
            return DispatchResult.BadRequest("missing_field: $FIELD_REASON")
        }

        return DispatchResult.Success(AgentSignal.FailWorkflow(reason))
    }

    companion object {
        private const val FIELD_HANDSHAKE_GUID = "handshakeGuid"
        private const val FIELD_RESULT = "result"
        private const val FIELD_REASON = "reason"

        private val DONE_RESULT_MAP = mapOf(
            ProtocolVocabulary.DoneResult.COMPLETED to DoneResult.COMPLETED,
            ProtocolVocabulary.DoneResult.PASS to DoneResult.PASS,
            ProtocolVocabulary.DoneResult.NEEDS_ITERATION to DoneResult.NEEDS_ITERATION,
        )
    }
}

/**
 * Result of a signal callback dispatch.
 *
 * Maps to HTTP response codes when served via the HTTP server:
 * - [Success] → 200
 * - [SessionNotFound] → 404
 * - [BadRequest] → 400
 */
sealed class DispatchResult {
    /** Signal was successfully dispatched and the deferred was completed. */
    data class Success(val signal: AgentSignal) : DispatchResult()

    /** No session found for the given [guid]. */
    data class SessionNotFound(val guid: HandshakeGuid) : DispatchResult()

    /** The payload was invalid or missing required fields. */
    data class BadRequest(val message: String) : DispatchResult()
}
