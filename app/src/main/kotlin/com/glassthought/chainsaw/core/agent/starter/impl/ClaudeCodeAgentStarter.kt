package com.glassthought.chainsaw.core.agent.starter.impl

import com.glassthought.chainsaw.core.agent.data.TmuxStartCommand
import com.glassthought.chainsaw.core.agent.starter.AgentStarter

/**
 * Builds the `claude` CLI command for starting a Claude Code agent in a tmux session.
 *
 * All configuration is provided at construction time by the
 * [com.glassthought.chainsaw.core.agent.impl.ClaudeCodeAgentStarterBundleFactory].
 *
 * @param workingDir Directory the agent operates in. Used as `cd` target before launching claude.
 * @param model Claude model alias (e.g., "sonnet", "opus").
 * @param allowedTools Tools the agent is allowed to use (e.g., ["Read", "Write"]).
 * @param systemPrompt System prompt text, or null to use Claude's default prompt.
 * @param appendSystemPrompt When true, uses `--append-system-prompt` (preserves built-in prompt).
 *                           When false, uses `--system-prompt` (replaces built-in prompt).
 *                           Ignored when [systemPrompt] is null.
 * @param dangerouslySkipPermissions When true, adds `--dangerously-skip-permissions` flag.
 *                                   Required for non-interactive tmux usage.
 */
class ClaudeCodeAgentStarter(
    private val workingDir: String,
    private val model: String,
    private val allowedTools: List<String>,
    private val systemPrompt: String?,
    private val appendSystemPrompt: Boolean,
    private val dangerouslySkipPermissions: Boolean,
) : AgentStarter {

    override fun buildStartCommand(): TmuxStartCommand {
        val parts = mutableListOf("claude")

        parts.add("--model")
        parts.add(model)

        if (allowedTools.isNotEmpty()) {
            parts.add("--allowedTools")
            parts.add(allowedTools.joinToString(","))
        }

        if (systemPrompt != null) {
            val flag = if (appendSystemPrompt) {
                "--append-system-prompt"
            } else {
                "--system-prompt"
            }
            parts.add(flag)
            // Shell-quote the prompt to handle spaces and special characters.
            parts.add("\"${escapeForDoubleQuote(systemPrompt)}\"")
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

        /**
         * Escapes a string for safe inclusion inside double quotes within a bash command.
         */
        private fun escapeForDoubleQuote(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`")
    }
}
