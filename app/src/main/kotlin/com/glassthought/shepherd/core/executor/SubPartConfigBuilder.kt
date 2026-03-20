package com.glassthought.shepherd.core.executor

import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.context.ExecutionContext
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.state.SubPartRole
import java.nio.file.Path

/**
 * Builds [SubPartConfig] instances from [Part] and [SubPart] model objects.
 *
 * Resolves paths via [AiOutputStructure] (execution vs planning phase), maps
 * role strings to [RoleDefinition], and assembles [ExecutionContext] with
 * prior public MD paths from preceding parts.
 *
 * Stateless — all state comes from constructor parameters and method arguments.
 */
class SubPartConfigBuilder(
    private val aiOutputStructure: AiOutputStructure,
    private val roleDefinitions: Map<String, RoleDefinition>,
    private val ticketContent: String,
    private val planMdPath: Path?,
) {

    /**
     * Builds the [SubPartConfig] for a specific sub-part within a part.
     *
     * @param part The parent part containing this sub-part.
     * @param subPartIndex Index of the sub-part within [Part.subParts] (0 = doer, 1 = reviewer).
     * @param priorPublicMdPaths PUBLIC.md paths from all completed prior parts (in execution order).
     * @return A fully-resolved [SubPartConfig] ready for [PartExecutorImpl].
     * @throws IllegalArgumentException if [SubPart.role] does not match any loaded role definition,
     *   or if [SubPart.agentType] is not a valid [AgentType].
     */
    fun build(
        part: Part,
        subPartIndex: Int,
        priorPublicMdPaths: List<Path>,
    ): SubPartConfig {
        val subPart = part.subParts[subPartIndex]
        val subPartRole = SubPartRole.fromIndex(subPartIndex)
        val roleDefinition = resolveRoleDefinition(subPart)
        val agentType = parseAgentType(subPart)

        val publicMdPath = resolvePublicMdPath(part, subPart)
        val privateMdPath = resolvePrivateMdPath(part, subPart)
        val outputDir = resolveOutputDir(part, subPart)

        val executionContext = ExecutionContext(
            partName = part.name,
            partDescription = part.description,
            planMdPath = planMdPath,
            priorPublicMdPaths = priorPublicMdPaths,
        )

        val doerPublicMdPath = if (subPartRole == SubPartRole.REVIEWER) {
            resolveDoerPublicMdPath(part)
        } else {
            null
        }

        val feedbackDir = if (subPartRole == SubPartRole.REVIEWER && part.phase == Phase.EXECUTION) {
            aiOutputStructure.feedbackDir(part.name)
        } else {
            null
        }

        return SubPartConfig(
            partName = part.name,
            subPartName = subPart.name,
            subPartIndex = subPartIndex,
            subPartRole = subPartRole,
            agentType = agentType,
            model = subPart.model,
            systemPromptPath = roleDefinition.filePath,
            bootstrapMessage = BOOTSTRAP_MESSAGE,
            roleDefinition = roleDefinition,
            ticketContent = ticketContent,
            outputDir = outputDir,
            publicMdOutputPath = publicMdPath,
            privateMdPath = privateMdPath,
            executionContext = executionContext,
            doerPublicMdPath = doerPublicMdPath,
            feedbackDir = feedbackDir,
        )
    }

    private fun resolveRoleDefinition(subPart: SubPart): RoleDefinition {
        return roleDefinitions[subPart.role]
            ?: throw IllegalArgumentException(
                "No role definition found for role=[${subPart.role}]. " +
                    "Available roles: ${roleDefinitions.keys.sorted()}"
            )
    }

    private fun parseAgentType(subPart: SubPart): AgentType {
        return try {
            AgentType.valueOf(subPart.agentType.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid agentType=[${subPart.agentType}] for sub-part=[${subPart.name}]. " +
                    "Valid values: ${AgentType.entries.map { it.name }}",
                e,
            )
        }
    }

    private fun resolvePublicMdPath(part: Part, subPart: SubPart): Path = when (part.phase) {
        Phase.PLANNING -> aiOutputStructure.planningPublicMd(subPart.name)
        Phase.EXECUTION -> aiOutputStructure.executionPublicMd(part.name, subPart.name)
    }

    private fun resolvePrivateMdPath(part: Part, subPart: SubPart): Path = when (part.phase) {
        Phase.PLANNING -> aiOutputStructure.planningPrivateMd(subPart.name)
        Phase.EXECUTION -> aiOutputStructure.executionPrivateMd(part.name, subPart.name)
    }

    private fun resolveOutputDir(part: Part, subPart: SubPart): Path = when (part.phase) {
        Phase.PLANNING -> aiOutputStructure.planningCommOutDir(subPart.name)
        Phase.EXECUTION -> aiOutputStructure.executionCommOutDir(part.name, subPart.name)
    }

    private fun resolveDoerPublicMdPath(part: Part): Path {
        val doerSubPart = part.subParts[SubPartRole.DOER_INDEX]
        return resolvePublicMdPath(part, doerSubPart)
    }

    companion object {
        private const val BOOTSTRAP_MESSAGE = "Waiting for instructions."
    }
}
