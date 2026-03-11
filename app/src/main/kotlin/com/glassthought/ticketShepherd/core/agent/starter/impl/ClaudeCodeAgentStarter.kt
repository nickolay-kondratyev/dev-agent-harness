package com.glassthought.ticketShepherd.core.agent.starter.impl

import com.glassthought.ticketShepherd.core.agent.data.TmuxStartCommand
import com.glassthought.ticketShepherd.core.agent.starter.AgentStarter

/**
 * Builds the `claude` CLI command for starting a Claude Code agent in a tmux session.
 *
 * @param workingDir Directory the agent operates in. Used as `cd` target before launching claude.
 * @param model Claude model alias (e.g., "sonnet", "opus").
 * @param tools Tools available to the agent (e.g., ["Bash", "Read", "Write", "Edit"]).
 *   Uses `--tools` which **restricts** the available tool set and reduces context window size.
 *   This is distinct from `--allowedTools` which only pre-approves tools for permissions
 *   but keeps all tools available (and in the context window).
 * @param systemPromptFilePath Absolute path to the system prompt file, or null to use default.
 * @param appendSystemPrompt When true, uses `--append-system-prompt-file` (preserves built-in prompt).
 *                           When false, uses `--system-prompt-file` (replaces built-in prompt).
 *                           Ignored when [systemPromptFilePath] is null.
 * @param dangerouslySkipPermissions When true, adds `--dangerously-skip-permissions` flag.
 *                                   Required for non-interactive tmux usage.
 */
class ClaudeCodeAgentStarter(
    private val workingDir: String,
    private val model: String,
    private val tools: List<String>,
    private val systemPromptFilePath: String?,
    private val appendSystemPrompt: Boolean,
    private val dangerouslySkipPermissions: Boolean,
) : AgentStarter {

    override fun buildStartCommand(): TmuxStartCommand {
        val parts = mutableListOf("claude")

        parts.add("--model")
        parts.add(model)

        if (tools.isNotEmpty()) {
            // [--tools]: restricts available tool set and reduces context window size.
            // Distinct from --allowedTools which only pre-approves permissions but keeps
            // all tools available (and in the context window).
            parts.add("--tools")
            parts.add(tools.joinToString(","))
        }

        if (systemPromptFilePath != null) {
            val flag = if (appendSystemPrompt) {
                "--append-system-prompt-file"
            } else {
                "--system-prompt-file"
            }
            parts.add(flag)
            parts.add(systemPromptFilePath)
        }

        if (dangerouslySkipPermissions) {
            parts.add("--dangerously-skip-permissions")
        }

        val claudeCommand = parts.joinToString(" ")
        // [unset CLAUDECODE]: Claude Code refuses to start when the CLAUDECODE env var is set
        // (nested session detection). The harness spawns Claude in a tmux session which inherits
        // the parent environment, so we must explicitly unset it.
        val innerCommand = "cd $workingDir && unset CLAUDECODE && $claudeCommand"
        val fullCommand = "bash -c '${escapeForShell(innerCommand)}'"

        return TmuxStartCommand(fullCommand)
    }

    companion object {
        /**
         * Escapes a string for safe inclusion in a single-quoted bash -c command.
         * Replaces single quotes with the idiom: end quote, escaped quote, start quote.
         */
        private fun escapeForShell(value: String): String =
            value.replace("'", "'\\''")
    }
}
