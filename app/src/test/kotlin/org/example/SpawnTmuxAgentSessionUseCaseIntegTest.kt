package org.example

import com.glassthought.chainsaw.core.agent.AgentStarterBundleFactory
import com.glassthought.chainsaw.core.agent.AgentTypeChooser
import com.glassthought.chainsaw.core.agent.DefaultAgentTypeChooser
import com.glassthought.chainsaw.core.agent.SpawnTmuxAgentSessionUseCase
import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.agent.impl.ClaudeCodeAgentStarterBundleFactory
import com.glassthought.chainsaw.core.data.AgentType
import com.glassthought.chainsaw.core.data.PhaseType
import com.glassthought.chainsaw.core.initializer.data.Environment
import com.glassthought.chainsaw.core.tmux.TmuxSession
import com.glassthought.chainsaw.integtest.SharedAppDepDescribeSpec
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File
import java.nio.file.Path

/**
 * Integration test for [SpawnTmuxAgentSessionUseCase].
 *
 * Spawns a real Claude Code session in tmux, performs the GUID handshake,
 * and verifies the returned [com.glassthought.chainsaw.core.agent.TmuxAgentSession].
 *
 * Requires: tmux installed, claude CLI installed and authenticated.
 * Gated with [isIntegTestEnabled].
 */
@OptIn(ExperimentalKotest::class)
class SpawnTmuxAgentSessionUseCaseIntegTest : SharedAppDepDescribeSpec({

    describe("GIVEN SpawnTmuxAgentSessionUseCase with test configuration").config(isIntegTestEnabled()) {
        val sessionManager = appDependencies.tmuxSessionManager

        val systemPromptFilePath = resolveSystemPromptFilePath()
        val out = outFactory.getOutForClass(SpawnTmuxAgentSessionUseCaseIntegTest::class)

        val bundleFactory: AgentStarterBundleFactory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = systemPromptFilePath,
            claudeProjectsDir = Path.of(System.getProperty("user.home"), ".claude", "projects"),
            outFactory = outFactory,
        )

        val agentTypeChooser: AgentTypeChooser = DefaultAgentTypeChooser()

        val useCase = SpawnTmuxAgentSessionUseCase(
            agentTypeChooser = agentTypeChooser,
            bundleFactory = bundleFactory,
            tmuxSessionManager = sessionManager,
            outFactory = outFactory,
        )

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

/**
 * Resolves the absolute path to the test system prompt file.
 *
 * Walks up from the current working directory to find the git repo root,
 * then resolves `config/prompts/test-agent-system-prompt.txt`.
 */
private fun resolveSystemPromptFilePath(): String {
    val repoRoot = findGitRepoRoot(File(System.getProperty("user.dir")))
    val promptFile = File(repoRoot, "config/prompts/test-agent-system-prompt.txt")
    require(promptFile.exists()) {
        "System prompt file not found at [${promptFile.absolutePath}]"
    }
    return promptFile.absolutePath
}

private fun findGitRepoRoot(startDir: File): File {
    var dir: File? = startDir
    while (dir != null) {
        if (File(dir, ".git").exists()) {
            return dir
        }
        dir = dir.parentFile
    }
    throw IllegalStateException("Could not find .git directory starting from [${startDir.absolutePath}]")
}
