package com.glassthought.shepherd.usecase.spawn

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.ShepherdValType
import com.glassthought.shepherd.core.agent.TmuxAgentSession
import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.agent.tmux.TmuxSessionCreator
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.data.HealthTimeoutLadder
import com.glassthought.shepherd.core.state.AgentSessionInfo
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.SessionRecord
import com.glassthought.shepherd.core.time.Clock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Orchestrates Phase 1 of the agent spawn flow: bootstrap identity + liveness handshake.
 *
 * 1. Generates a [HandshakeGuid]
 * 2. Builds TMUX session name (`shepherd_${partName}_${subPartName}`)
 * 3. Builds start command via [AgentTypeAdapter.buildStartCommand]
 * 4. Creates TMUX session via [TmuxSessionCreator.createSession]
 * 5. Awaits `/callback-shepherd/signal/started` within [HealthTimeoutLadder.startup]
 * 6. Resolves session ID via [AgentTypeAdapter.resolveSessionId]
 * 7. Stores [SessionRecord] in [CurrentState]
 * 8. Returns [TmuxAgentSession]
 *
 * See spec: ref.ap.hZdTRho3gQwgIXxoUtTqy.E
 */
@AnchorPoint("ap.M1jzg6RlJkYL4hi8aXr7LnQA.E")
class SpawnTmuxAgentSessionUseCase(
    private val agentTypeAdapters: Map<AgentType, AgentTypeAdapter>,
    private val tmuxSessionCreator: TmuxSessionCreator,
    private val healthTimeoutLadder: HealthTimeoutLadder,
    private val currentState: CurrentState,
    private val clock: Clock,
    outFactory: OutFactory,
) {

    private val out = outFactory.getOutForClass(SpawnTmuxAgentSessionUseCase::class)

    /**
     * Executes Phase 1 bootstrap spawn flow.
     *
     * @param params All parameters needed to spawn the agent session.
     * @return [TmuxAgentSession] containing the live TMUX session handle and resolved agent identity.
     * @throws TmuxSessionCreationException if TMUX session creation fails.
     * @throws StartupTimeoutException if the agent does not acknowledge startup within the configured timeout.
     * @throws IllegalArgumentException if no [AgentTypeAdapter] is registered for the given [AgentType].
     */
    suspend fun execute(params: SpawnTmuxAgentSessionParams): TmuxAgentSession {
        val adapter = requireAdapter(params.agentType)
        val handshakeGuid = HandshakeGuid.generate()
        val sessionName = buildSessionName(params.partName, params.subPartName)

        out.info(
            "spawning_agent_session",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(handshakeGuid.value, ShepherdValType.HANDSHAKE_GUID),
            Val(params.agentType.name, ValType.STRING_USER_AGNOSTIC),
        )

        val startCommand = adapter.buildStartCommand(
            BuildStartCommandParams(
                bootstrapMessage = params.bootstrapMessage,
                handshakeGuid = handshakeGuid,
                workingDir = params.workingDir,
                model = params.model,
                tools = params.tools,
                systemPromptFilePath = params.systemPromptFilePath,
                appendSystemPrompt = params.appendSystemPrompt,
            )
        )

        val tmuxSession = createTmuxSession(sessionName, startCommand)

        awaitStartupSignal(sessionName, params)

        val sessionId = adapter.resolveSessionId(handshakeGuid)

        val resumableAgentSessionId = ResumableAgentSessionId(
            handshakeGuid = handshakeGuid,
            agentType = params.agentType,
            sessionId = sessionId,
            model = params.model,
        )

        storeSessionRecord(params, handshakeGuid, sessionId)

        out.info(
            "agent_session_spawned",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(handshakeGuid.value, ShepherdValType.HANDSHAKE_GUID),
        )

        return TmuxAgentSession(
            tmuxSession = tmuxSession,
            resumableAgentSessionId = resumableAgentSessionId,
        )
    }

    private fun requireAdapter(agentType: AgentType): AgentTypeAdapter {
        return agentTypeAdapters[agentType]
            ?: throw IllegalArgumentException(
                "No AgentTypeAdapter registered for agentType=[$agentType]. " +
                    "Available: ${agentTypeAdapters.keys}"
            )
    }

    private fun buildSessionName(partName: String, subPartName: String): String {
        return "shepherd_${partName}_${subPartName}"
    }

    private suspend fun createTmuxSession(
        sessionName: String,
        startCommand: com.glassthought.shepherd.core.agent.data.TmuxStartCommand,
    ): com.glassthought.shepherd.core.agent.tmux.TmuxSession {
        return try {
            tmuxSessionCreator.createSession(sessionName, startCommand)
        } catch (e: IllegalStateException) {
            throw TmuxSessionCreationException(sessionName = sessionName, cause = e)
        }
    }

    private suspend fun awaitStartupSignal(
        sessionName: String,
        params: SpawnTmuxAgentSessionParams,
    ) {
        try {
            withTimeout(healthTimeoutLadder.startup) {
                params.startedDeferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            throw StartupTimeoutException(
                sessionName = sessionName,
                timeout = healthTimeoutLadder.startup,
                cause = e,
            )
        }
    }

    private fun storeSessionRecord(
        params: SpawnTmuxAgentSessionParams,
        handshakeGuid: HandshakeGuid,
        sessionId: String,
    ) {
        val record = SessionRecord(
            handshakeGuid = handshakeGuid.value,
            agentSession = AgentSessionInfo(id = sessionId),
            agentType = params.agentType.name,
            model = params.model,
            timestamp = clock.now().toString(),
        )

        currentState.addSessionRecord(params.partName, params.subPartName, record)
    }
}
