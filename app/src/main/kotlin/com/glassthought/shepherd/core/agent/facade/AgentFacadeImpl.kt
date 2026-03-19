package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.exception.base.AsgardBaseException
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.contextwindow.ContextWindowStateReader
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.agent.tmux.TmuxSession
import com.glassthought.shepherd.core.agent.tmux.TmuxSessionCreator
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.question.QaDrainAndDeliverUseCase
import com.glassthought.shepherd.core.server.AckedPayloadSender
import com.glassthought.shepherd.core.server.PayloadAckTimeoutException
import com.glassthought.shepherd.core.session.SessionEntry
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.usecase.healthmonitoring.AgentUnresponsiveUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.DetectionContext
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import com.glassthought.shepherd.usecase.healthmonitoring.UnresponsiveDiagnostics
import com.glassthought.shepherd.usecase.healthmonitoring.UnresponsiveHandleResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Real [AgentFacade] implementation that delegates to infrastructure components.
 *
 * Owns the [SessionsState] lifecycle — no external access to the session registry.
 * Each method performs structured logging via [Out].
 *
 * **sendPayloadAndAwaitSignal** implements the full health-aware await loop
 * (ref.ap.QCjutDexa2UBDaKB3jTcF.E): ACK-wrapped payload delivery, health monitoring
 * with ping/crash detection, and Q&A handling.
 *
 * Constructor depends on interfaces ([TmuxSessionCreator], [SingleSessionKiller]) rather
 * than the concrete [com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager],
 * following DIP and enabling unit testing without a real tmux binary.
 *
 * See ref.ap.9h0KS4EOK5yumssRCJdbq.E (AgentFacade spec).
 */
@AnchorPoint("ap.YRqz4vJhWbKc3NxTmAp8s.E")
@Suppress("LongParameterList", "TooManyFunctions")
class AgentFacadeImpl(
    private val sessionsState: SessionsState,
    private val agentTypeAdapter: AgentTypeAdapter,
    private val tmuxSessionCreator: TmuxSessionCreator,
    private val sessionKiller: SingleSessionKiller,
    private val contextWindowStateReader: ContextWindowStateReader,
    private val clock: Clock,
    private val harnessTimeoutConfig: HarnessTimeoutConfig,
    private val ackedPayloadSender: AckedPayloadSender,
    private val agentUnresponsiveUseCase: AgentUnresponsiveUseCase,
    private val qaDrainAndDeliverUseCase: QaDrainAndDeliverUseCase,
    private val outFactory: OutFactory,
) : AgentFacade {

    private val out = outFactory.getOutForClass(AgentFacadeImpl::class)

    override suspend fun spawnAgent(
        config: SpawnAgentConfig,
    ): SpawnedAgentHandle {
        val handshakeGuid = HandshakeGuid.generate()
        val sessionName = buildSessionName(config.partName, config.subPartName)

        out.info(
            "spawning_agent",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(handshakeGuid.value, ShepherdValType.HANDSHAKE_GUID),
            Val(config.agentType.name, ValType.STRING_USER_AGNOSTIC),
        )

        val signalDeferred = registerPlaceholderEntry(handshakeGuid, config)
        val tmuxSession = createTmuxSession(sessionName, config, handshakeGuid)
        awaitStartupOrCleanup(signalDeferred, handshakeGuid, sessionName, tmuxSession)

        val resumableId = resolveAndBuildSessionId(handshakeGuid, config)
        val tmuxAgentSession = TmuxAgentSession(
            tmuxSession = tmuxSession,
            resumableAgentSessionId = resumableId,
        )
        registerRealEntry(handshakeGuid, config, tmuxAgentSession)

        out.info(
            "agent_spawned",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(handshakeGuid.value, ShepherdValType.HANDSHAKE_GUID),
        )

        return SpawnedAgentHandle(
            guid = handshakeGuid,
            sessionId = resumableId,
            lastActivityTimestamp = clock.now(),
        )
    }

    override suspend fun killSession(handle: SpawnedAgentHandle) {
        out.info(
            "killing_agent_session",
            Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
        )

        val entry = sessionsState.lookup(handle.guid)
        if (entry != null) {
            sessionKiller.killSession(entry.tmuxAgentSession.tmuxSession)
            sessionsState.remove(handle.guid)
        }

        out.info(
            "agent_session_killed",
            Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
        )
    }

    override suspend fun readContextWindowState(
        handle: SpawnedAgentHandle,
    ): ContextWindowState {
        return contextWindowStateReader.read(handle.sessionId.sessionId)
    }

    /**
     * Delivers payload via [AckedPayloadSender] and runs the health-aware await
     * loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) until a signal arrives or a crash
     * is detected.
     *
     * See [HealthAwareAwaitLoop] for the monitoring logic.
     */
    override suspend fun sendPayloadAndAwaitSignal(
        handle: SpawnedAgentHandle,
        payload: AgentPayload,
    ): AgentSignal {
        out.info(
            "sending_payload_and_awaiting_signal",
            Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
        )

        val existingEntry = sessionsState.lookup(handle.guid)
            ?: error("No SessionEntry found for guid=[${handle.guid}]")

        val freshSignalDeferred = CompletableDeferred<AgentSignal>()
        val updatedEntry = SessionEntry(
            tmuxAgentSession = existingEntry.tmuxAgentSession,
            partName = existingEntry.partName,
            subPartName = existingEntry.subPartName,
            subPartIndex = existingEntry.subPartIndex,
            signalDeferred = freshSignalDeferred,
            lastActivityTimestamp = AtomicReference(clock.now()),
            questionQueue = existingEntry.questionQueue,
        )
        sessionsState.register(handle.guid, updatedEntry)

        val crashedFromAck = deliverPayloadOrCrash(
            handle, updatedEntry, payload, freshSignalDeferred,
        )
        if (crashedFromAck != null) return crashedFromAck

        out.info(
            "payload_delivered_entering_health_loop",
            Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
        )

        val loop = HealthAwareAwaitLoop(
            handle = handle,
            entry = updatedEntry,
            signalDeferred = freshSignalDeferred,
            tmuxSession = updatedEntry.tmuxAgentSession.tmuxSession,
            commInDir = payload.instructionFilePath.parent,
        )
        return loop.run()
    }

    /**
     * Attempts ACK-wrapped payload delivery. Returns [AgentSignal.Crashed]
     * if all ACK retries are exhausted, `null` on success.
     */
    private suspend fun deliverPayloadOrCrash(
        handle: SpawnedAgentHandle,
        entry: SessionEntry,
        payload: AgentPayload,
        signalDeferred: CompletableDeferred<AgentSignal>,
    ): AgentSignal.Crashed? {
        try {
            ackedPayloadSender.sendAndAwaitAck(
                tmuxSession = entry.tmuxAgentSession,
                sessionEntry = entry,
                payloadContent = payload.instructionFilePath.toString(),
            )
            return null
        } catch (e: PayloadAckTimeoutException) {
            out.info(
                "payload_ack_timeout_declaring_crash",
                Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
            )
            killSession(handle)
            val crashed = AgentSignal.Crashed(
                "Payload ACK timeout — ${e.message}"
            )
            signalDeferred.complete(crashed)
            return crashed
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private suspend fun registerPlaceholderEntry(
        guid: HandshakeGuid,
        config: SpawnAgentConfig,
    ): CompletableDeferred<AgentSignal> {
        val signalDeferred = CompletableDeferred<AgentSignal>()
        val entry = SessionEntry(
            tmuxAgentSession = PLACEHOLDER_TMUX_AGENT_SESSION,
            partName = config.partName,
            subPartName = config.subPartName,
            subPartIndex = config.subPartIndex,
            signalDeferred = signalDeferred,
            lastActivityTimestamp = AtomicReference(clock.now()),
            questionQueue = ConcurrentLinkedQueue(),
        )
        sessionsState.register(guid, entry)
        return signalDeferred
    }

    private suspend fun createTmuxSession(
        sessionName: String,
        config: SpawnAgentConfig,
        handshakeGuid: HandshakeGuid,
    ): TmuxSession {
        val startCommand = agentTypeAdapter.buildStartCommand(
            BuildStartCommandParams(
                bootstrapMessage = config.bootstrapMessage,
                handshakeGuid = handshakeGuid,
                workingDir = System.getProperty("user.dir"),
                model = config.model,
                tools = emptyList(),
                systemPromptFilePath = config.systemPromptPath.toString(),
                appendSystemPrompt = true,
            )
        )
        return tmuxSessionCreator.createSession(sessionName, startCommand)
    }

    private suspend fun awaitStartupOrCleanup(
        signalDeferred: CompletableDeferred<AgentSignal>,
        guid: HandshakeGuid,
        sessionName: String,
        tmuxSession: TmuxSession,
    ) {
        try {
            withTimeout(harnessTimeoutConfig.healthTimeouts.startup) {
                signalDeferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            sessionsState.remove(guid)
            sessionKiller.killSession(tmuxSession)
            throw AgentSpawnException(
                sessionName = sessionName,
                timeout = harnessTimeoutConfig.healthTimeouts.startup,
                cause = e,
            )
        }
    }

    private suspend fun resolveAndBuildSessionId(
        guid: HandshakeGuid,
        config: SpawnAgentConfig,
    ): ResumableAgentSessionId {
        val resolvedSessionId = agentTypeAdapter.resolveSessionId(guid)
        return ResumableAgentSessionId(
            handshakeGuid = guid,
            agentType = config.agentType,
            sessionId = resolvedSessionId,
            model = config.model,
        )
    }

    private suspend fun registerRealEntry(
        guid: HandshakeGuid,
        config: SpawnAgentConfig,
        tmuxAgentSession: TmuxAgentSession,
    ) {
        val realEntry = SessionEntry(
            tmuxAgentSession = tmuxAgentSession,
            partName = config.partName,
            subPartName = config.subPartName,
            subPartIndex = config.subPartIndex,
            signalDeferred = CompletableDeferred(),
            lastActivityTimestamp = AtomicReference(clock.now()),
            questionQueue = ConcurrentLinkedQueue(),
        )
        sessionsState.register(guid, realEntry)
    }

    private fun buildSessionName(
        partName: String,
        subPartName: String,
    ): String = "shepherd_${partName}_${subPartName}"

    /**
     * Encapsulates the health-aware await loop logic.
     *
     * Polls every [HarnessTimeoutConfig.healthCheckInterval] and checks:
     * (a) signal completion, (b) Q&A pending, (c) activity staleness.
     *
     * See ref.ap.QCjutDexa2UBDaKB3jTcF.E and ref.ap.6HIM68gd4kb8D2WmvQDUK.E.
     */
    private inner class HealthAwareAwaitLoop(
        private val handle: SpawnedAgentHandle,
        private val entry: SessionEntry,
        private val signalDeferred: CompletableDeferred<AgentSignal>,
        private val tmuxSession: TmuxSession,
        private val commInDir: java.nio.file.Path,
    ) {
        private val interval = harnessTimeoutConfig.healthCheckInterval
        private val normalActivity = harnessTimeoutConfig.healthTimeouts.normalActivity
        private val pingResponse = harnessTimeoutConfig.healthTimeouts.pingResponse

        @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
        suspend fun run(): AgentSignal {
            while (true) {
                delay(interval)

                checkSignalCompleted()?.let { return it }

                if (entry.isQAPending) {
                    drainQA()
                    continue
                }

                val staleResult = checkStaleness()
                if (staleResult == StalenessAction.FRESH) continue

                // Stale — ping sent, now await proof of life
                val pingResult = awaitPingProofOfLife()
                when (pingResult) {
                    is PingOutcome.SignalArrived -> return pingResult.signal
                    is PingOutcome.AgentAlive -> continue
                    is PingOutcome.Crashed -> return pingResult.signal
                }
            }
        }

        private suspend fun checkSignalCompleted(): AgentSignal? {
            if (!signalDeferred.isCompleted) return null
            val signal = signalDeferred.await()
            out.info(
                "signal_received",
                Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(signal::class.simpleName ?: "unknown", ShepherdValType.SIGNAL_ACTION),
            )
            return signal
        }

        private suspend fun drainQA() {
            out.info(
                "qa_pending_draining",
                Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
            )
            qaDrainAndDeliverUseCase.drainAndDeliver(entry, commInDir)
        }

        /**
         * Checks `lastActivityTimestamp` staleness. If stale, sends a ping
         * via [AgentUnresponsiveUseCase]. Returns [StalenessAction.FRESH]
         * if no action needed, [StalenessAction.PING_SENT] if ping was sent.
         */
        private suspend fun checkStaleness(): StalenessAction {
            val lastActivity = entry.lastActivityTimestamp.get()
            val ageMs = java.time.Duration.between(lastActivity, clock.now()).toMillis()
            val callbackAge = ageMs.milliseconds

            if (callbackAge < normalActivity) {
                out.debug("last_activity_timestamp_fresh") {
                    listOf(
                        Val(callbackAge.toString(), ShepherdValType.CALLBACK_AGE),
                        Val(normalActivity.toString(), ShepherdValType.TIMEOUT_THRESHOLD),
                    )
                }
                return StalenessAction.FRESH
            }

            out.info(
                "no_activity_timeout_triggering_ping",
                Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(callbackAge.toString(), ShepherdValType.STALE_DURATION),
                Val(normalActivity.toString(), ShepherdValType.TIMEOUT_THRESHOLD),
            )

            agentUnresponsiveUseCase.handle(
                detectionContext = DetectionContext.NO_ACTIVITY_TIMEOUT,
                tmuxSession = tmuxSession,
                diagnostics = UnresponsiveDiagnostics(
                    handshakeGuid = handle.guid,
                    timeoutDuration = normalActivity,
                    staleDuration = callbackAge,
                ),
            )
            return StalenessAction.PING_SENT
        }

        /**
         * Waits the [pingResponse] window, polling for proof of life.
         */
        @Suppress("ReturnCount") // Three outcomes: signal, alive, crashed.
        private suspend fun awaitPingProofOfLife(): PingOutcome {
            val timestampBeforePing = entry.lastActivityTimestamp.get()
            var elapsed = 0L
            val intervalMs = interval.inWholeMilliseconds
            val pingResponseMs = pingResponse.inWholeMilliseconds

            while (elapsed < pingResponseMs) {
                delay(interval)
                elapsed += intervalMs

                if (signalDeferred.isCompleted) {
                    return PingOutcome.SignalArrived(signalDeferred.await())
                }

                val current = entry.lastActivityTimestamp.get()
                if (current.isAfter(timestampBeforePing)) {
                    out.info(
                        "agent_alive_after_ping",
                        Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
                    )
                    return PingOutcome.AgentAlive
                }
            }

            return declareCrash()
        }

        private suspend fun declareCrash(): PingOutcome.Crashed {
            out.info(
                "ping_timeout_killing_session",
                Val(handle.guid.value, ShepherdValType.HANDSHAKE_GUID),
                Val(tmuxSession.name.sessionName, ShepherdValType.TMUX_SESSION_NAME),
            )

            val finalTs = entry.lastActivityTimestamp.get()
            val staleMs = java.time.Duration.between(finalTs, clock.now()).toMillis()

            val result = agentUnresponsiveUseCase.handle(
                detectionContext = DetectionContext.PING_TIMEOUT,
                tmuxSession = tmuxSession,
                diagnostics = UnresponsiveDiagnostics(
                    handshakeGuid = handle.guid,
                    timeoutDuration = pingResponse,
                    staleDuration = staleMs.milliseconds,
                ),
            )

            val crashed = when (result) {
                is UnresponsiveHandleResult.SessionKilled -> result.signal
                is UnresponsiveHandleResult.PingSent -> AgentSignal.Crashed(
                    "Unexpected PingSent for PING_TIMEOUT context"
                )
            }
            signalDeferred.complete(crashed)
            sessionsState.remove(handle.guid)
            return PingOutcome.Crashed(crashed)
        }
    }

    companion object {
        private val PLACEHOLDER_TMUX_AGENT_SESSION: TmuxAgentSession by lazy {
            val noOpCommunicator =
                object : com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator {
                    override suspend fun sendKeys(paneTarget: String, text: String) =
                        error("Placeholder — sendKeys must not be called")

                    override suspend fun sendRawKeys(paneTarget: String, keys: String) =
                        error("Placeholder — sendRawKeys must not be called")
                }
            val noOpExistsChecker =
                com.glassthought.shepherd.core.agent.tmux.SessionExistenceChecker {
                    false
                }
            val placeholderSession =
                com.glassthought.shepherd.core.agent.tmux.TmuxSession(
                    name = com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName(
                        "__placeholder__"
                    ),
                    paneTarget = "__placeholder__:0.0",
                    communicator = noOpCommunicator,
                    existsChecker = noOpExistsChecker,
                )
            TmuxAgentSession(
                tmuxSession = placeholderSession,
                resumableAgentSessionId = ResumableAgentSessionId(
                    handshakeGuid = HandshakeGuid("handshake.placeholder"),
                    agentType = com.glassthought.shepherd.core.data.AgentType.CLAUDE_CODE,
                    sessionId = "__placeholder__",
                    model = "__placeholder__",
                ),
            )
        }
    }
}

/** Staleness check result — used internally by [HealthAwareAwaitLoop]. */
private enum class StalenessAction { FRESH, PING_SENT }

/** Outcome of the ping proof-of-life phase — used by [HealthAwareAwaitLoop]. */
private sealed class PingOutcome {
    data class SignalArrived(val signal: AgentSignal) : PingOutcome()
    data object AgentAlive : PingOutcome()
    data class Crashed(val signal: AgentSignal) : PingOutcome()
}


/**
 * Thrown when agent spawning fails (TMUX creation failure, startup timeout, etc.).
 *
 * @property sessionName The TMUX session name that failed to spawn.
 * @property timeout The startup timeout duration that was exceeded.
 */
class AgentSpawnException(
    val sessionName: String,
    val timeout: Duration,
    cause: Throwable? = null,
) : AsgardBaseException(
    "agent_spawn_failed",
    cause,
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(timeout.toString(), ValType.STRING_USER_AGNOSTIC),
)
