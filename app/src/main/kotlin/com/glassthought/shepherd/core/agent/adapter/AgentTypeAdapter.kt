package com.glassthought.shepherd.core.agent.adapter

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid

/**
 * Unified interface for agent-type-specific behavior in the spawn flow.
 *
 * Each agent type (Claude Code, PI, etc.) provides a single implementation that
 * encapsulates both the start command builder and the session ID resolver.
 * This eliminates the risk of mismatching starter/resolver pairs and simplifies wiring.
 *
 * See ref.ap.A0L92SUzkG3gE0gX04ZnK.E for the design rationale.
 *
 * ## Why a single interface instead of separate `AgentStarter` + `AgentSessionIdResolver`
 *
 * 1. Always deployed together — every caller that builds a start command also resolves the
 *    session ID. Separate interfaces create the risk of wiring mismatched pairs.
 * 2. Simpler wiring — one constructor parameter instead of two.
 * 3. Agent-specific concerns are encapsulated (e.g., Claude Code's `unset CLAUDECODE`
 *    and JSONL scanning).
 */
@AnchorPoint("ap.hhP3gT9qK2mR8vNwX5dYa.E")
interface AgentTypeAdapter {

    /**
     * Builds the shell command to start an agent in a TMUX session.
     *
     * The returned [TmuxStartCommand] is passed to
     * [com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager.createSession].
     *
     * @param params Per-session parameters including bootstrap message, handshake GUID,
     *   working directory, model, tools, and system prompt configuration.
     */
    fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand

    /**
     * Resolves the agent's internal session ID from the handshake GUID.
     *
     * Called **after** the agent has acknowledged startup (i.e., after `/signal/started`
     * is received), so the GUID is guaranteed to be in the agent's session artifacts.
     *
     * @param handshakeGuid The GUID that was sent to the agent during bootstrap.
     * @return The raw session ID string (e.g., UUID from the JSONL filename for Claude Code).
     *   The caller constructs [com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId].
     * @throws IllegalStateException if no session or multiple sessions match the GUID.
     */
    suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String
}

/**
 * Per-session parameters for [AgentTypeAdapter.buildStartCommand].
 *
 * These are all values that vary per agent session, as opposed to adapter-level
 * configuration (e.g., `claudeProjectsDir`, logging, timeouts) which is wired once
 * at initialization.
 *
 * @param bootstrapMessage The bootstrap message to embed as a positional CLI argument.
 *   Contains the handshake GUID and a startup acknowledgment instruction.
 * @param handshakeGuid The GUID for this session — exported as an env var in the start command.
 * @param workingDir Directory the agent operates in. Used as `cd` target before launching the agent.
 * @param model Agent model alias (e.g., "sonnet", "opus").
 * @param tools Tools available to the agent (e.g., ["Bash", "Read", "Write", "Edit"]).
 * @param systemPromptFilePath Absolute path to the system prompt file, or null to use default.
 * @param appendSystemPrompt When true, uses `--append-system-prompt-file` (preserves built-in prompt).
 *   When false, uses `--system-prompt-file` (replaces built-in prompt). Ignored when
 *   [systemPromptFilePath] is null.
 */
data class BuildStartCommandParams(
    val bootstrapMessage: String,
    val handshakeGuid: HandshakeGuid,
    val workingDir: String,
    val model: String,
    val tools: List<String>,
    val systemPromptFilePath: String?,
    val appendSystemPrompt: Boolean,
)
