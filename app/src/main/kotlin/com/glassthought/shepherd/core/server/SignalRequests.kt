package com.glassthought.shepherd.core.server

/**
 * Request DTOs for the `/callback-shepherd/signal/{action}` endpoints.
 *
 * Each DTO maps 1:1 to an HTTP callback the agent can fire.
 * Jackson deserializes these from JSON — all fields are required unless noted.
 */

/** POST /callback-shepherd/signal/started */
data class SignalStartedRequest(val handshakeGuid: String)

/** POST /callback-shepherd/signal/done — [result] must match agent's role (DOER/REVIEWER). */
data class SignalDoneRequest(val handshakeGuid: String, val result: String)

/** POST /callback-shepherd/signal/user-question */
data class SignalUserQuestionRequest(val handshakeGuid: String, val question: String)

/** POST /callback-shepherd/signal/fail-workflow */
data class SignalFailWorkflowRequest(val handshakeGuid: String, val reason: String)

/** POST /callback-shepherd/signal/ack-payload — [payloadId] must match [SessionEntry.pendingPayloadAck]. */
data class SignalAckPayloadRequest(val handshakeGuid: String, val payloadId: String)

/** POST /callback-shepherd/signal/self-compacted */
data class SignalSelfCompactedRequest(val handshakeGuid: String)
