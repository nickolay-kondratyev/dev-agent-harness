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
import com.glassthought.shepherd.core.session.SessionEntry
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Real [AgentFacade] implementation that delegates to infrastructure components.
 *
 * Owns the [SessionsState] lifecycle — no external access to the session registry.
 * Each method performs structured logging via [Out].
 *
 * **sendPayloadAndAwaitSignal** is a V1 stub — full health-aware await loop is deferred
 * to a separate ticket (nid_qdd1w86a415xllfpvcsf8djab_E).
 *
 * Constructor depends on interfaces ([TmuxSessionCreator], [SingleSessionKiller]) rather
 * than the concrete [com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager], following DIP
 * and enabling unit testing without a real tmux binary.
 *
 * See ref.ap.9h0KS4EOK5yumssRCJdbq.E (AgentFacade spec).
 */
@AnchorPoint("ap.YRqz4vJhWbKc3NxTmAp8s.E")
class AgentFacadeImpl(
    private val sessionsState: SessionsState,
    private val agentTypeAdapter: AgentTypeAdapter,
    private val tmuxSessionCreator: TmuxSessionCreator,
    private val sessionKiller: SingleSessionKiller,
    private val contextWindowStateReader: ContextWindowStateReader,
    private val clock: Clock,
    private val harnessTimeoutConfig: HarnessTimeoutConfig,
    private val outFactory: OutFactory,
) : AgentFacade {

    private val out = outFactory.getOutForClass(AgentFacadeImpl::class)

    override suspend fun spawnAgent(config: SpawnAgentConfig): SpawnedAgentHandle {
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
        val tmuxAgentSession = TmuxAgentSession(tmuxSession = tmuxSession, resumableAgentSessionId = resumableId)
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

    override suspend fun readContextWindowState(handle: SpawnedAgentHandle): ContextWindowState {
        return contextWindowStateReader.read(handle.sessionId.sessionId)
    }

    /**
     * V1 stub — delivers payload and awaits the signal deferred.
     *
     * **V1 limitation**: Sends the raw instruction file path via TMUX send-keys without
     * ACK protocol wrapping (ref.ap.tbtBcVN2iCl1xfHJthllP.E). The full ACK-wrapped delivery
     * via [com.glassthought.shepherd.core.server.AckedPayloadSender] and health-aware await loop
     * (health pings, Q&A handling, crash detection) are deferred to ticket
     * nid_qdd1w86a415xllfpvcsf8djab_E.
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

        // V1 stub: sends raw path without ACK wrapping. See KDoc above.
        updatedEntry.tmuxAgentSession.tmuxSession.sendKeys(payload.instructionFilePath.toString())

        return freshSignalDeferred.await()
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Registers a placeholder [SessionEntry] so the HTTP server can find the entry
     * by [HandshakeGuid] during the startup handshake.
     *
     * @return The [CompletableDeferred] that the HTTP server will complete on /signal/started.
     */
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

    private fun buildSessionName(partName: String, subPartName: String): String {
        return "shepherd_${partName}_${subPartName}"
    }

    companion object {
        /**
         * Placeholder [TmuxAgentSession] used in the initial [SessionEntry] registration
         * before the real TMUX session is created. Replaced after startup completes.
         */
        private val PLACEHOLDER_TMUX_AGENT_SESSION: TmuxAgentSession by lazy {
            val noOpCommunicator = object : com.glassthought.shepherd.core.agent.tmux.TmuxCommunicator {
                override suspend fun sendKeys(paneTarget: String, text: String) =
                    error("Placeholder — sendKeys must not be called")

                override suspend fun sendRawKeys(paneTarget: String, keys: String) =
                    error("Placeholder — sendRawKeys must not be called")
            }
            val noOpExistsChecker = com.glassthought.shepherd.core.agent.tmux.SessionExistenceChecker {
                false
            }
            val placeholderTmuxSession = com.glassthought.shepherd.core.agent.tmux.TmuxSession(
                name = com.glassthought.shepherd.core.agent.tmux.data.TmuxSessionName("__placeholder__"),
                paneTarget = "__placeholder__:0.0",
                communicator = noOpCommunicator,
                existsChecker = noOpExistsChecker,
            )
            TmuxAgentSession(
                tmuxSession = placeholderTmuxSession,
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


/**
 * Thrown when agent spawning fails (TMUX creation failure, startup timeout, etc.).
 *
 * @property sessionName The TMUX session name that failed to spawn.
 * @property timeout The startup timeout duration that was exceeded.
 */
class AgentSpawnException(
    val sessionName: String,
    val timeout: kotlin.time.Duration,
    cause: Throwable? = null,
) : AsgardBaseException(
    "agent_spawn_failed",
    cause,
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(timeout.toString(), ValType.STRING_USER_AGNOSTIC),
)
