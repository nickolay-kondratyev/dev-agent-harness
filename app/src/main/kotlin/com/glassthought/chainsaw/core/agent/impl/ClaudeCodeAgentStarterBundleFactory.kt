package com.glassthought.chainsaw.core.agent.impl

import com.asgard.core.out.OutFactory
import com.glassthought.chainsaw.core.agent.AgentStarterBundle
import com.glassthought.chainsaw.core.agent.AgentStarterBundleFactory
import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.agent.starter.impl.ClaudeCodeAgentStarter
import com.glassthought.chainsaw.core.data.AgentType
import com.glassthought.chainsaw.core.initializer.data.Environment
import com.glassthought.chainsaw.core.wingman.impl.ClaudeCodeAgentSessionIdResolver
import java.io.File
import java.nio.file.Path

/**
 * Creates [AgentStarterBundle] for [AgentType.CLAUDE_CODE].
 *
 * Uses [Environment.isTest] to determine agent configuration:
 * - **Test mode**: `--model sonnet`, `--allowedTools Read,Write`, `--system-prompt`,
 *   `--dangerously-skip-permissions`. Minimal configuration to reduce cost.
 * - **Production mode**: `--model sonnet`, `--allowedTools Bash,Edit,Read,Write,Glob,Grep`,
 *   `--append-system-prompt`, `--dangerously-skip-permissions`.
 *
 * @param environment Runtime environment (test vs production).
 * @param systemPromptFilePath Path to a file containing the system prompt text, or null for default behavior.
 *   The file content is read at bundle creation time and passed inline to the claude CLI.
 * @param claudeProjectsDir Directory where Claude stores session JSONL files (typically `~/.claude/projects`).
 * @param outFactory Factory for structured logging, passed to the session ID resolver.
 */
class ClaudeCodeAgentStarterBundleFactory(
    private val environment: Environment,
    private val systemPromptFilePath: String?,
    private val claudeProjectsDir: Path,
    private val outFactory: OutFactory,
) : AgentStarterBundleFactory {

    override fun create(agentType: AgentType, request: StartAgentRequest): AgentStarterBundle {
        require(agentType == AgentType.CLAUDE_CODE) {
            "ClaudeCodeAgentStarterBundleFactory only supports CLAUDE_CODE, got [$agentType]"
        }

        val systemPrompt = readSystemPrompt()

        val starter = if (environment.isTest) {
            ClaudeCodeAgentStarter(
                workingDir = request.workingDir,
                model = TEST_MODEL,
                allowedTools = TEST_ALLOWED_TOOLS,
                systemPrompt = systemPrompt,
                appendSystemPrompt = false,
                dangerouslySkipPermissions = true,
            )
        } else {
            ClaudeCodeAgentStarter(
                workingDir = request.workingDir,
                model = PRODUCTION_MODEL,
                allowedTools = PRODUCTION_ALLOWED_TOOLS,
                systemPrompt = systemPrompt,
                appendSystemPrompt = true,
                dangerouslySkipPermissions = true,
            )
        }

        val sessionIdResolver = ClaudeCodeAgentSessionIdResolver(
            claudeProjectsDir = claudeProjectsDir,
            outFactory = outFactory,
        )

        return AgentStarterBundle(
            starter = starter,
            sessionIdResolver = sessionIdResolver,
        )
    }

    /**
     * Reads the system prompt from the configured file path.
     * Returns null if no file path is configured.
     */
    private fun readSystemPrompt(): String? {
        if (systemPromptFilePath == null) return null

        val file = File(systemPromptFilePath)
        require(file.exists()) {
            "System prompt file not found at [$systemPromptFilePath]"
        }
        return file.readText().trim()
    }

    companion object {
        private const val TEST_MODEL = "sonnet"
        private val TEST_ALLOWED_TOOLS = listOf("Read", "Write")

        private const val PRODUCTION_MODEL = "sonnet"
        private val PRODUCTION_ALLOWED_TOOLS = listOf("Bash", "Edit", "Read", "Write", "Glob", "Grep")
    }
}
