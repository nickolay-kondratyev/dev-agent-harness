package com.glassthought.shepherd.core.agent.facade

/**
 * Programmable [AgentFacade] fake for unit testing the orchestration layer.
 *
 * Each facade method delegates to a user-supplied handler lambda. Handlers default to
 * throwing [IllegalStateException] so that missing setup is caught immediately (fail hard,
 * never mask). **Exception**: [killSession] defaults to a no-op (just records the call),
 * since kill is typically not the behavior under test. All calls are recorded for interaction
 * verification.
 *
 * Usage:
 * ```kotlin
 * val fake = FakeAgentFacade()
 * fake.onSpawn { config -> someHandle }
 * fake.onSendPayloadAndAwaitSignal { handle, payload -> AgentSignal.Done(DoneResult.COMPLETED) }
 * ```
 *
 * See ref.ap.9h0KS4EOK5yumssRCJdbq.E (AgentFacade spec) for context.
 */
class FakeAgentFacade : AgentFacade {

    // ── Recorded interactions ──────────────────────────────────────────────

    private val _spawnCalls = mutableListOf<SpawnAgentConfig>()
    private val _sendPayloadCalls = mutableListOf<SendPayloadCall>()
    private val _readContextWindowStateCalls = mutableListOf<SpawnedAgentHandle>()
    private val _killSessionCalls = mutableListOf<SpawnedAgentHandle>()

    /** All [spawnAgent] calls in order. */
    val spawnCalls: List<SpawnAgentConfig> get() = _spawnCalls.toList()

    /** All [sendPayloadAndAwaitSignal] calls in order. */
    val sendPayloadCalls: List<SendPayloadCall> get() = _sendPayloadCalls.toList()

    /** All [readContextWindowState] calls in order. */
    val readContextWindowStateCalls: List<SpawnedAgentHandle> get() = _readContextWindowStateCalls.toList()

    /** All [killSession] calls in order. */
    val killSessionCalls: List<SpawnedAgentHandle> get() = _killSessionCalls.toList()

    // ── Handler lambdas (programmatic control) ─────────────────────────────

    private var spawnHandler: suspend (SpawnAgentConfig) -> SpawnedAgentHandle = {
        error("FakeAgentFacade: spawnAgent not programmed. Call onSpawn { ... } first.")
    }

    private var sendPayloadHandler: suspend (SpawnedAgentHandle, AgentPayload) -> AgentSignal =
        { _, _ ->
            error(
                "FakeAgentFacade: sendPayloadAndAwaitSignal not programmed. " +
                    "Call onSendPayloadAndAwaitSignal { ... } first."
            )
        }

    private var readContextWindowStateHandler: suspend (SpawnedAgentHandle) -> ContextWindowState = {
        error("FakeAgentFacade: readContextWindowState not programmed. Call onReadContextWindowState { ... } first.")
    }

    private var killSessionHandler: suspend (SpawnedAgentHandle) -> Unit = {
        // Default: just record the call (kill is often a no-op in tests)
    }

    // ── Programming methods ────────────────────────────────────────────────

    /** Programs the behavior of [spawnAgent]. */
    fun onSpawn(handler: suspend (SpawnAgentConfig) -> SpawnedAgentHandle) {
        spawnHandler = handler
    }

    /** Programs the behavior of [sendPayloadAndAwaitSignal]. */
    fun onSendPayloadAndAwaitSignal(handler: suspend (SpawnedAgentHandle, AgentPayload) -> AgentSignal) {
        sendPayloadHandler = handler
    }

    /** Programs the behavior of [readContextWindowState]. */
    fun onReadContextWindowState(handler: suspend (SpawnedAgentHandle) -> ContextWindowState) {
        readContextWindowStateHandler = handler
    }

    /** Programs the behavior of [killSession]. */
    fun onKillSession(handler: suspend (SpawnedAgentHandle) -> Unit) {
        killSessionHandler = handler
    }

    // ── AgentFacade implementation ─────────────────────────────────────────

    override suspend fun spawnAgent(config: SpawnAgentConfig): SpawnedAgentHandle {
        _spawnCalls.add(config)
        return spawnHandler(config)
    }

    override suspend fun sendPayloadAndAwaitSignal(
        handle: SpawnedAgentHandle,
        payload: AgentPayload,
    ): AgentSignal {
        _sendPayloadCalls.add(SendPayloadCall(handle, payload))
        return sendPayloadHandler(handle, payload)
    }

    override suspend fun readContextWindowState(handle: SpawnedAgentHandle): ContextWindowState {
        _readContextWindowStateCalls.add(handle)
        return readContextWindowStateHandler(handle)
    }

    override suspend fun killSession(handle: SpawnedAgentHandle) {
        _killSessionCalls.add(handle)
        killSessionHandler(handle)
    }

    // ── Supporting types ───────────────────────────────────────────────────

    /** Recorded call to [sendPayloadAndAwaitSignal]. */
    data class SendPayloadCall(
        val handle: SpawnedAgentHandle,
        val payload: AgentPayload,
    )
}
