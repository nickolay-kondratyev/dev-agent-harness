package com.glassthought.shepherd.usecase.spawn

import com.glassthought.shepherd.core.data.AgentType
import kotlinx.coroutines.CompletableDeferred

/**
 * Bundles all parameters needed by [SpawnTmuxAgentSessionUseCase.execute] to bootstrap
 * an agent in a TMUX session.
 *
 * @property partName Name of the plan part (e.g., "part_1_backend").
 * @property subPartName Name of the sub-part within the part (e.g., "doer", "reviewer").
 * @property agentType Type of agent to spawn — used to select the correct [com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter].
 * @property model Model identifier for the agent (e.g., "claude-sonnet-4-20250514").
 * @property workingDir Directory the agent operates in. Used as `cd` target before launching the agent.
 * @property tools Tools available to the agent (e.g., ["Bash", "Read", "Write", "Edit"]).
 * @property systemPromptFilePath Absolute path to the system prompt file, or null to use default.
 * @property appendSystemPrompt When true, uses `--append-system-prompt-file`; when false, uses `--system-prompt-file`.
 * @property bootstrapMessage Initial message containing handshake GUID and startup instruction.
 * @property startedDeferred Completed by the server when `/callback-shepherd/signal/started` is received.
 */
data class SpawnTmuxAgentSessionParams(
    val partName: String,
    val subPartName: String,
    val agentType: AgentType,
    val model: String,
    val workingDir: String,
    val tools: List<String>,
    val systemPromptFilePath: String?,
    val appendSystemPrompt: Boolean,
    val bootstrapMessage: String,
    val startedDeferred: CompletableDeferred<Unit>,
)
