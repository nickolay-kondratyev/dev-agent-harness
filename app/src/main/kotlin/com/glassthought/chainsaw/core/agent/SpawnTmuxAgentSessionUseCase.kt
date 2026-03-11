package com.glassthought.chainsaw.core.agent

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.tmux.TmuxSessionManager
import com.glassthought.chainsaw.core.wingman.HandshakeGuid
import java.util.UUID

/**
 * Orchestrates the full agent spawn flow:
 * 1. Choose agent type from request
 * 2. Create agent-specific starter bundle (command builder + session ID resolver)
 * 3. Build the tmux start command
 * 4. Create a tmux session running the agent
 * 5. Send the GUID handshake marker
 * 6. Resolve the agent session ID via polling
 * 7. Return a [TmuxAgentSession] pairing the live tmux handle with the session identity
 *
 * Dependencies are injected via constructor; no production wiring into AppDependencies
 * is performed in this ticket.
 */
class SpawnTmuxAgentSessionUseCase(
    private val agentTypeChooser: AgentTypeChooser,
    private val bundleFactory: AgentStarterBundleFactory,
    private val tmuxSessionManager: TmuxSessionManager,
    outFactory: OutFactory,
) {
    private val out = outFactory.getOutForClass(SpawnTmuxAgentSessionUseCase::class)

    /**
     * Spawns a new agent session in tmux, performs the GUID handshake, and returns
     * the resulting [TmuxAgentSession].
     *
     * @param request Contains the phase type and working directory for the agent.
     * @return A [TmuxAgentSession] with a live tmux session and resolved session identity.
     * @throws IllegalStateException if tmux session creation or GUID resolution fails.
     */
    suspend fun spawn(request: StartAgentRequest): TmuxAgentSession {
        val guid = HandshakeGuid(UUID.randomUUID().toString())

        out.info(
            "spawning_agent_session",
            Val(request.phaseType.name, ValType.STRING_USER_AGNOSTIC),
            Val(guid.value, ValType.STRING_USER_AGNOSTIC),
        )

        val agentType = agentTypeChooser.choose(request)

        out.info(
            "agent_type_chosen",
            Val(agentType.name, ValType.STRING_USER_AGNOSTIC),
        )

        val bundle = bundleFactory.create(agentType, request)
        val startCommand = bundle.starter.buildStartCommand()

        out.info(
            "start_command_built",
            Val(startCommand.command, ValType.SHELL_COMMAND),
        )

        val sessionName = "chainsaw-${request.phaseType.name.lowercase()}-${System.currentTimeMillis()}"

        val tmuxSession = tmuxSessionManager.createSession(sessionName, startCommand.command)

        out.info(
            "tmux_session_created_sending_guid",
            Val(sessionName, ValType.STRING_USER_AGNOSTIC),
            Val(guid.value, ValType.STRING_USER_AGNOSTIC),
        )

        tmuxSession.sendKeys(guid.value)

        out.info(
            "guid_sent_resolving_session_id",
            Val(guid.value, ValType.STRING_USER_AGNOSTIC),
        )

        val sessionId = bundle.sessionIdResolver.resolveSessionId(guid)

        out.info(
            "agent_session_spawned",
            Val(sessionId.sessionId, ValType.STRING_USER_AGNOSTIC),
            Val(sessionId.agentType.name, ValType.STRING_USER_AGNOSTIC),
        )

        return TmuxAgentSession(
            tmuxSession = tmuxSession,
            resumableAgentSessionId = sessionId,
        )
    }
}
