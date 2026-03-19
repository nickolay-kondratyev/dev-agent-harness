package com.glassthought.shepherd.core.executor

import com.glassthought.shepherd.core.agent.facade.SpawnAgentConfig
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.context.ExecutionContext
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.state.SubPartRole
import java.nio.file.Path

/**
 * Static configuration for a single sub-part (doer or reviewer) within a [PartExecutorImpl].
 *
 * Carries everything needed to:
 * 1. Create [SpawnAgentConfig] for [AgentFacade.spawnAgent]
 * 2. Create [AgentInstructionRequest] for [ContextForAgentProvider.assembleInstructions]
 * 3. Create [SubPartDoneContext] for [GitCommitStrategy.onSubPartDone]
 * 4. Validate PUBLIC.md after done signal
 *
 * Intentionally minimal — holds static configuration that does not change across iterations.
 * Mutable iteration state lives in [PartExecutorImpl].
 */
data class SubPartConfig(
    // -- identity --
    val partName: String,
    val subPartName: String,
    val subPartIndex: Int,
    val subPartRole: SubPartRole,

    // -- agent spawn --
    val agentType: AgentType,
    val model: String,
    val systemPromptPath: Path,
    val bootstrapMessage: String,

    // -- instruction assembly --
    val roleDefinition: RoleDefinition,
    val ticketContent: String,
    val outputDir: Path,
    val publicMdOutputPath: Path,
    val privateMdPath: Path?,
    val executionContext: ExecutionContext,

    // -- reviewer-specific (only populated for reviewer sub-parts) --
    /** Path to the doer's PUBLIC.md, used when assembling reviewer instructions. */
    val doerPublicMdPath: Path? = null,
    /** Feedback directory for reviewer instructions. */
    val feedbackDir: Path? = null,
) {

    /**
     * Builds the [SpawnAgentConfig] needed by [AgentFacade.spawnAgent].
     */
    fun toSpawnAgentConfig(): SpawnAgentConfig = SpawnAgentConfig(
        partName = partName,
        subPartName = subPartName,
        subPartIndex = subPartIndex,
        agentType = agentType,
        model = model,
        role = subPartRole.name,
        systemPromptPath = systemPromptPath,
        bootstrapMessage = bootstrapMessage,
    )
}
