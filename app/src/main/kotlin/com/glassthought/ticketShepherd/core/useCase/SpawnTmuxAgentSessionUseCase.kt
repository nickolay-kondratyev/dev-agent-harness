package com.glassthought.ticketShepherd.core.useCase

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.ticketShepherd.core.agent.AgentStarterBundleFactory
import com.glassthought.ticketShepherd.core.agent.AgentTypeChooser
import com.glassthought.ticketShepherd.core.agent.TmuxAgentSession
import com.glassthought.ticketShepherd.core.agent.data.StartAgentRequest
import com.glassthought.ticketShepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxSessionManager
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Specification of SpawnTmuxAgentSessionUseCase in ref.ap.hZdTRho3gQwgIXxoUtTqy.E
 *
 * TODO: MISALIGNED WITH SPEC. ADJUST TO ALIGN WITH SPEC.
 */
@AnchorPoint("ap.M1jzg6RlJkYL4hi8aXr7LnQA.E")
class SpawnTmuxAgentSessionUseCase(
  private val agentTypeChooser: AgentTypeChooser,
  private val bundleFactory: AgentStarterBundleFactory,
  private val tmuxSessionManager: TmuxSessionManager,
  private val agentStartupDelay: Duration = DEFAULT_AGENT_STARTUP_DELAY,
  outFactory: OutFactory,
) {
    private val out = outFactory.getOutForClass(SpawnTmuxAgentSessionUseCase::class)

    /**
     * Spawns a new agent session in tmux, performs the GUID handshake, and returns
     * the resulting [com.glassthought.ticketShepherd.core.agent.TmuxAgentSession].
     *
     * @param request Contains the phase type and working directory for the agent.
     * @return A [com.glassthought.ticketShepherd.core.agent.TmuxAgentSession] with a live tmux session and resolved session identity.
     * @throws IllegalStateException if tmux session creation or GUID resolution fails.
     */
    suspend fun spawn(request: StartAgentRequest): TmuxAgentSession {
        val guid = HandshakeGuid.Companion.generate()

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

        val sessionName = "shepherd-${request.phaseType.name.lowercase()}-${System.currentTimeMillis()}"

        val tmuxSession = tmuxSessionManager.createSession(sessionName, startCommand.command)

        out.info(
            "tmux_session_created_waiting_for_agent_startup",
          Val(sessionName, ValType.STRING_USER_AGNOSTIC),
          Val(agentStartupDelay.toString(), ValType.STRING_USER_AGNOSTIC),
        )

        // Wait for the agent CLI to initialize its interactive prompt before
        // sending the GUID. Without this delay, the GUID would be consumed by
        // the shell (bash -c) rather than the agent's input handler.
      delay(agentStartupDelay)

        out.info(
            "sending_guid_to_tmux_session",
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

    companion object {
        /** Default delay to let the agent CLI start up before sending the GUID. */
        val DEFAULT_AGENT_STARTUP_DELAY: Duration = 5.seconds
    }
}