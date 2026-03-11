package com.glassthought.chainsaw.core.agent.impl

import com.asgard.core.out.OutFactory
import com.glassthought.chainsaw.core.agent.AgentStarterBundle
import com.glassthought.chainsaw.core.agent.AgentStarterBundleFactory
import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.agent.starter.impl.ClaudeCodeAgentStarter
import com.glassthought.chainsaw.core.data.AgentType
import com.glassthought.chainsaw.core.initializer.data.Environment
import com.glassthought.chainsaw.core.wingman.impl.ClaudeCodeAgentSessionIdResolver
import java.nio.file.Path

/**
 * Creates [AgentStarterBundle] for [AgentType.CLAUDE_CODE].
 *
 * Uses [Environment.isTest] to determine agent configuration:
 * - **Test mode**: `--model sonnet`, `--tools Bash,Read,Write,Edit`, `--system-prompt-file`,
 *   `--dangerously-skip-permissions`. Minimal configuration to reduce cost and context window.
 * - **Production mode**: `--model sonnet`, `--tools Bash,Edit,Read,Write,Glob,Grep`,
 *   `--append-system-prompt-file`, `--dangerously-skip-permissions`.
 *
 * @param environment Runtime environment (test vs production).
 * @param systemPromptFilePath Absolute path to a system prompt file, or null for default behavior.
 *   Passed directly to the claude CLI via `--system-prompt-file` or `--append-system-prompt-file`.
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

        val starter = if (environment.isTest) {
            ClaudeCodeAgentStarter(
                workingDir = request.workingDir,
                model = TEST_MODEL,
                tools = TEST_TOOLS,
                systemPromptFilePath = systemPromptFilePath,
                appendSystemPrompt = false,
                dangerouslySkipPermissions = true,
            )
        } else {
            ClaudeCodeAgentStarter(
                workingDir = request.workingDir,
                model = PRODUCTION_MODEL,
                tools = PRODUCTION_TOOLS,
                systemPromptFilePath = systemPromptFilePath,
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

    companion object {
        private const val TEST_MODEL = "sonnet"
        private val TEST_TOOLS = listOf("Bash", "Read", "Write", "Edit")

        private const val PRODUCTION_MODEL = "sonnet"
        private val PRODUCTION_TOOLS = listOf("Bash", "Edit", "Read", "Write", "Glob", "Grep")
    }
}
