package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import java.time.Instant

/**
 * Opaque handle to a spawned agent session, returned by [AgentFacade.spawnAgent].
 *
 * The handle is the executor's reference to an agent — passed to all subsequent
 * [AgentFacade] calls ([sendPayloadAndAwaitSignal][AgentFacade.sendPayloadAndAwaitSignal],
 * [readContextWindowState][AgentFacade.readContextWindowState],
 * [killSession][AgentFacade.killSession]).
 *
 * The [lastActivityTimestamp] is updated by the real facade implementation on every HTTP
 * callback from the agent. In [FakeAgentFacade] tests, it is controlled directly by the test.
 * Uses `@Volatile` for visibility across coroutines — sufficient for single-writer patterns
 * where only the facade (or test) updates the value.
 *
 * @property guid The handshake GUID used to identify this agent session in the server's
 *   session registry (ref.ap.7V6upjt21tOoCFXA7nqNh.E).
 * @property sessionId The resolved resumable session ID containing agent type, session ID,
 *   and model information.
 * @property lastActivityTimestamp Timestamp of the most recent activity (HTTP callback)
 *   from the agent. Updated by the facade on every callback. Initialized to the spawn time.
 */
@AnchorPoint("ap.kWchUPtTLqu73qXLHbKMs.E")
data class SpawnedAgentHandle(
    val guid: HandshakeGuid,
    val sessionId: ResumableAgentSessionId,
    @Volatile var lastActivityTimestamp: Instant,
)
