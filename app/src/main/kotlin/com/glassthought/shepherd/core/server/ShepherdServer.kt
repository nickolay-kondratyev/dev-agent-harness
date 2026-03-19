package com.glassthought.shepherd.core.server

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import com.glassthought.shepherd.core.question.UserQuestionContext
import com.glassthought.shepherd.core.session.SessionEntry
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.state.SubPartRole
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.Instant

/**
 * Embedded Ktor CIO HTTP server handling agent-to-harness signal callbacks.
 *
 * All endpoints live under `POST /callback-shepherd/signal/{action}`.
 * Each request includes a `handshakeGuid` that the server looks up in [sessionsState]
 * to find the [SessionEntry] for the calling agent.
 *
 * **Lifecycle signals** (`done`, `fail-workflow`, `self-compacted`) complete the
 * [SessionEntry.signalDeferred] — exactly once. Duplicate completions are idempotent
 * (200 + WARN log).
 *
 * **Side-channel signals** (`started`, `user-question`, `ack-payload`) update session
 * state without completing the deferred.
 *
 * All callbacks update [SessionEntry.lastActivityTimestamp] to reset health monitoring.
 *
 * See agent-to-server-communication-protocol.md (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E).
 */
@AnchorPoint("ap.shepherdServerKtorCio.E")
class ShepherdServer(
    private val sessionsState: SessionsState,
    outFactory: OutFactory,
) {

    private val out = outFactory.getOutForClass(ShepherdServer::class)

    /**
     * Installs content negotiation and signal routes on the given [Application].
     *
     * Extracted as a top-level function so that both production (embedded CIO) and
     * test (Ktor `testApplication`) can share the same routing configuration.
     */
    fun configureApplication(application: Application) {
        application.install(ContentNegotiation) {
            jackson()
        }
        application.routing {
            route("/callback-shepherd/signal") {
                post("/started") { handleStarted() }
                post("/done") { handleDone() }
                post("/user-question") { handleUserQuestion() }
                post("/fail-workflow") { handleFailWorkflow() }
                post("/self-compacted") { handleSelfCompacted() }
                post("/ack-payload") { handleAckPayload() }
            }
        }
    }

    // ── Signal handlers ────────────────────────────────────────────────

    /** Side-channel: agent reports it has started. Update timestamp only. */
    private suspend fun io.ktor.server.routing.RoutingContext.handleStarted() {
        val request = call.receive<SignalStartedRequest>()
        val guid = HandshakeGuid(request.handshakeGuid)

        val entry = lookupOrRespond404(guid) ?: return

        updateTimestamp(entry)

        out.info(
            "signal_started_received",
            Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
        )

        call.respondText("OK", status = HttpStatusCode.OK)
    }

    /** Lifecycle: agent reports done with a result. Validates result against role. */
    private suspend fun io.ktor.server.routing.RoutingContext.handleDone() {
        val request = call.receive<SignalDoneRequest>()
        val guid = HandshakeGuid(request.handshakeGuid)

        val entry = lookupOrRespond404(guid) ?: return

        val doneResult = validateAndParseDoneResult(request.result, entry, guid) ?: return

        updateTimestamp(entry)
        val completed = entry.signalDeferred.complete(AgentSignal.Done(doneResult))

        if (!completed) {
            out.warn(
                "signal_done_duplicate",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(request.result, ShepherdValType.RESULT),
            )
        } else {
            out.info(
                "signal_done_completed",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(request.result, ShepherdValType.RESULT),
            )
        }

        call.respondText("OK", status = HttpStatusCode.OK)
    }

    /**
     * Parses and validates the result string for a done signal.
     * Returns the [DoneResult] if valid, or null after responding with 400.
     */
    @Suppress("ReturnCount")
    private suspend fun io.ktor.server.routing.RoutingContext.validateAndParseDoneResult(
        resultStr: String,
        entry: SessionEntry,
        guid: HandshakeGuid,
    ): DoneResult? {
        val doneResult = parseDoneResult(resultStr)
        if (doneResult == null) {
            out.warn(
                "signal_done_invalid_result",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(resultStr, ShepherdValType.RESULT),
            )
            call.respondText(
                "Invalid result=[$resultStr]. Expected one of: ${VALID_RESULTS.joinToString()}",
                status = HttpStatusCode.BadRequest,
            )
            return null
        }

        if (!isResultValidForRole(doneResult, entry.role)) {
            out.warn(
                "signal_done_role_mismatch",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(resultStr, ShepherdValType.RESULT),
                Val(entry.role.name, ShepherdValType.ROLE),
            )
            call.respondText(
                "Result=[$resultStr] is not valid for role=[${entry.role}]",
                status = HttpStatusCode.BadRequest,
            )
            return null
        }

        return doneResult
    }

    /** Side-channel: agent asks a user question. Append to queue. */
    private suspend fun io.ktor.server.routing.RoutingContext.handleUserQuestion() {
        val request = call.receive<SignalUserQuestionRequest>()
        val guid = HandshakeGuid(request.handshakeGuid)

        val entry = lookupOrRespond404(guid) ?: return

        updateTimestamp(entry)

        val questionContext = UserQuestionContext(
            question = request.question,
            partName = entry.partName,
            subPartName = entry.subPartName,
            subPartRole = entry.role,
            handshakeGuid = guid,
        )
        entry.questionQueue.add(questionContext)

        out.info(
            "signal_user_question_received",
            Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
        )

        call.respondText("OK", status = HttpStatusCode.OK)
    }

    /** Lifecycle: agent requests workflow failure. */
    private suspend fun io.ktor.server.routing.RoutingContext.handleFailWorkflow() {
        val request = call.receive<SignalFailWorkflowRequest>()
        val guid = HandshakeGuid(request.handshakeGuid)

        val entry = lookupOrRespond404(guid) ?: return

        updateTimestamp(entry)
        val completed = entry.signalDeferred.complete(AgentSignal.FailWorkflow(request.reason))

        if (!completed) {
            // Late fail-workflow after a done signal is an ERROR, not just a WARN
            out.error(
                "signal_fail_workflow_after_done",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(request.reason, ShepherdValType.REASON),
            )
        } else {
            out.info(
                "signal_fail_workflow_completed",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(request.reason, ShepherdValType.REASON),
            )
        }

        call.respondText("OK", status = HttpStatusCode.OK)
    }

    /** Lifecycle: agent completed self-compaction. */
    private suspend fun io.ktor.server.routing.RoutingContext.handleSelfCompacted() {
        val request = call.receive<SignalSelfCompactedRequest>()
        val guid = HandshakeGuid(request.handshakeGuid)

        val entry = lookupOrRespond404(guid) ?: return

        updateTimestamp(entry)
        val completed = entry.signalDeferred.complete(AgentSignal.SelfCompacted)

        if (!completed) {
            out.warn(
                "signal_self_compacted_duplicate",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
            )
        } else {
            out.info(
                "signal_self_compacted_completed",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
            )
        }

        call.respondText("OK", status = HttpStatusCode.OK)
    }

    /** Side-channel: agent acknowledges a payload delivery. */
    private suspend fun io.ktor.server.routing.RoutingContext.handleAckPayload() {
        val request = call.receive<SignalAckPayloadRequest>()
        val guid = HandshakeGuid(request.handshakeGuid)

        val entry = lookupOrRespond404(guid) ?: return

        updateTimestamp(entry)

        val pendingId = entry.pendingPayloadAck.get()

        if (pendingId == null) {
            out.warn(
                "signal_ack_payload_already_null",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(request.payloadId, ShepherdValType.PAYLOAD_ID),
            )
        } else if (pendingId.value != request.payloadId) {
            out.warn(
                "signal_ack_payload_mismatch",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(request.payloadId, ShepherdValType.PAYLOAD_ID),
                Val(pendingId.value, ShepherdValType.PAYLOAD_ID),
            )
            // WHY-NOT: We do NOT clear pendingPayloadAck on mismatch — the real ACK
            // may still arrive. Clearing prematurely would cause the sender to think
            // delivery succeeded when it may not have.
        } else {
            entry.pendingPayloadAck.set(null)
            out.info(
                "signal_ack_payload_cleared",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(request.payloadId, ShepherdValType.PAYLOAD_ID),
            )
        }

        call.respondText("OK", status = HttpStatusCode.OK)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Looks up the session entry for [guid]. If not found, responds with 404 and returns null.
     * Caller MUST return early when null is returned.
     */
    private suspend fun io.ktor.server.routing.RoutingContext.lookupOrRespond404(
        guid: HandshakeGuid,
    ): SessionEntry? {
        val entry = sessionsState.lookup(guid)
        if (entry == null) {
            out.warn(
                "signal_unknown_handshake_guid",
                Val(guid.value, ShepherdValType.HANDSHAKE_GUID),
            )
            call.respondText(
                "Unknown handshakeGuid=[${guid.value}]",
                status = HttpStatusCode.NotFound,
            )
        }
        return entry
    }

    private fun updateTimestamp(entry: SessionEntry) {
        entry.lastActivityTimestamp.set(Instant.now())
    }

    companion object {
        private val VALID_RESULTS = listOf(
            ProtocolVocabulary.DoneResult.COMPLETED,
            ProtocolVocabulary.DoneResult.PASS,
            ProtocolVocabulary.DoneResult.NEEDS_ITERATION,
        )

        private fun parseDoneResult(result: String): DoneResult? = when (result) {
            ProtocolVocabulary.DoneResult.COMPLETED -> DoneResult.COMPLETED
            ProtocolVocabulary.DoneResult.PASS -> DoneResult.PASS
            ProtocolVocabulary.DoneResult.NEEDS_ITERATION -> DoneResult.NEEDS_ITERATION
            else -> null
        }

        /**
         * Validates that [result] is valid for the given [role].
         *
         * DOER may only send [DoneResult.COMPLETED].
         * REVIEWER may only send [DoneResult.PASS] or [DoneResult.NEEDS_ITERATION].
         */
        private fun isResultValidForRole(result: DoneResult, role: SubPartRole): Boolean =
            when (role) {
                SubPartRole.DOER -> result == DoneResult.COMPLETED
                SubPartRole.REVIEWER -> result == DoneResult.PASS || result == DoneResult.NEEDS_ITERATION
            }
    }
}
