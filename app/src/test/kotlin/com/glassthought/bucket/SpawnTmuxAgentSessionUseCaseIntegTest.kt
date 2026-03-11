package com.glassthought.bucket

import com.glassthought.ticketShepherd.core.useCase.SpawnTmuxAgentSessionUseCase
import com.glassthought.ticketShepherd.core.agent.data.StartAgentRequest
import com.glassthought.ticketShepherd.core.data.AgentType
import com.glassthought.ticketShepherd.core.data.PhaseType
import com.glassthought.ticketShepherd.core.agent.tmux.TmuxSession
import com.glassthought.ticketShepherd.integtest.SharedContextDescribeSpec
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * Integration test for [SpawnTmuxAgentSessionUseCase].
 *
 * Spawns a real Claude Code session in tmux, performs the GUID handshake,
 * and verifies the returned [com.glassthought.ticketShepherd.core.agent.TmuxAgentSession].
 *
 * Requires: tmux installed, claude CLI installed and authenticated.
 * Gated with [isIntegTestEnabled].
 */
@OptIn(ExperimentalKotest::class)
class SpawnTmuxAgentSessionUseCaseIntegTest : SharedContextDescribeSpec({

    describe("GIVEN SpawnTmuxAgentSessionUseCase with test configuration").config(isIntegTestEnabled()) {
        val useCase = shepherdContext.useCases.spawnTmuxAgentSession
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val out = outFactory.getOutForClass(SpawnTmuxAgentSessionUseCaseIntegTest::class)

        val createdSessions = mutableListOf<TmuxSession>()

        afterEach {
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed.
                }
            }
            createdSessions.clear()
        }

        describe("WHEN spawn is called with IMPLEMENTOR phase") {
            // Single it block: spawning Claude sessions is expensive (API cost + time).
            // Multiple assertions verify different facets of the same result.
            it("THEN returns a TmuxAgentSession with valid tmux session and resolved session ID") {
                val request = StartAgentRequest(
                    phaseType = PhaseType.IMPLEMENTOR,
                    workingDir = System.getProperty("user.dir"),
                )

                val agentSession = useCase.spawn(request)
                createdSessions.add(agentSession.tmuxSession)

                agentSession.tmuxSession.exists() shouldBe true
                agentSession.resumableAgentSessionId.agentType shouldBe AgentType.CLAUDE_CODE
                agentSession.resumableAgentSessionId.sessionId.shouldNotBeBlank()

                val message =
                    "Spawned tmux session [${agentSession.tmuxSession.name}] with GUID [${agentSession.resumableAgentSessionId.sessionId}]"
                println(message)
                out.info(message)
            }
        }
    }
})
