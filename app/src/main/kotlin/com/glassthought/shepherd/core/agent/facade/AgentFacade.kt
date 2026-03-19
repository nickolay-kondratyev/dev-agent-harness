package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.annotation.AnchorPoint

/**
 * Single facade interface for all orchestration-layer interactions with agents.
 *
 * The orchestration layer ([PartExecutor]/ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) uses this facade
 * as its sole agent-facing dependency. The real implementation ([AgentFacadeImpl]) delegates
 * to infrastructure components (SessionsState, TmuxSessionManager, TmuxCommunicator,
 * ContextWindowStateReader, AgentUnresponsiveUseCase). A [FakeAgentFacade] enables comprehensive
 * unit testing of the orchestration state machine with virtual time.
 *
 * All methods are `suspend` — they are designed to be called from coroutine contexts.
 * Thread safety is guaranteed by the implementation.
 *
 * See spec: ref.ap.9h0KS4EOK5yumssRCJdbq.E (AgentFacade.md)
 */
@AnchorPoint("ap.1aEIkOGUeTijwvrACf3Ga.E")
interface AgentFacade {

    /**
     * Spawns a new agent session with the given [config].
     *
     * Internally performs: TMUX session creation, bootstrap message delivery, handshake
     * (waits for `/signal/started` callback), session ID resolution, and initial
     * [SessionsState] registration (ref.ap.7V6upjt21tOoCFXA7nqNh.E).
     *
     * @param config Configuration for the agent to spawn (part info, agent type, model, etc.).
     * @return A [SpawnedAgentHandle] that the executor uses for all subsequent interactions
     *   with this agent. The handle's [SpawnedAgentHandle.lastActivityTimestamp] is initialized
     *   to the spawn time.
     * @throws AgentSpawnException if the spawn or handshake fails (e.g., TMUX creation failure,
     *   bootstrap timeout, handshake timeout).
     */
    suspend fun spawnAgent(config: SpawnAgentConfig): SpawnedAgentHandle

    /**
     * Delivers [payload] to the agent identified by [handle] and suspends until the agent
     * produces an [AgentSignal].
     *
     * This method encapsulates the full signal lifecycle:
     * 1. Creates a fresh `CompletableDeferred<AgentSignal>` internally
     * 2. Re-registers the session entry (same HandshakeGuid, new deferred)
     * 3. Delivers the payload via TMUX send-keys with ACK protocol
     *    (ref.ap.tbtBcVN2iCl1xfHJthllP.E) — retries up to 3 times on ACK failure
     * 4. Runs the health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) until a signal
     *    arrives or a crash is detected
     *
     * **Q&A handling is internal to this method**: when the agent sends a user-question
     * callback, the health-aware await loop detects it, collects answers via
     * [UserQuestionHandler] (ref.ap.NE4puAzULta4xlOLh5kfD.E), delivers them back to the
     * agent, and resumes signal-await. Health pings and timeout are suppressed during Q&A.
     * The caller never sees or handles user questions — they are resolved transparently.
     *
     * @param handle The spawned agent handle from [spawnAgent].
     * @param payload The instruction payload to deliver to the agent.
     * @return The [AgentSignal] produced by the agent (Done, FailWorkflow, SelfCompacted)
     *   or by the health monitor (Crashed).
     */
    suspend fun sendPayloadAndAwaitSignal(handle: SpawnedAgentHandle, payload: AgentPayload): AgentSignal

    /**
     * Reads the current context window state for the agent identified by [handle].
     *
     * Used at done boundaries for self-compaction decisions
     * (ref.ap.8nwz2AHf503xwq8fKuLcl.E). NOT used inside the health-aware await loop —
     * liveness monitoring is based solely on [SpawnedAgentHandle.lastActivityTimestamp].
     *
     * @param handle The spawned agent handle from [spawnAgent].
     * @return [ContextWindowState] with [ContextWindowState.remainingPercentage] indicating
     *   the percentage of context window remaining (0–100), or `null` if the value is stale,
     *   unavailable, or could not be read from `context_window_slim.json`. Callers must
     *   handle `null` gracefully.
     */
    suspend fun readContextWindowState(handle: SpawnedAgentHandle): ContextWindowState

    /**
     * Kills the TMUX session for the agent identified by [handle] and performs cleanup.
     *
     * After this call, the [handle] is no longer valid for any other [AgentFacade] operations.
     * Calling other methods with a killed handle produces undefined behavior.
     *
     * This is called by the executor when a part completes or fails — all sessions for the
     * part are killed. It is also called internally by the facade when ACK retries are
     * exhausted (before returning [AgentSignal.Crashed]).
     *
     * @param handle The spawned agent handle to kill.
     */
    suspend fun killSession(handle: SpawnedAgentHandle)
}
