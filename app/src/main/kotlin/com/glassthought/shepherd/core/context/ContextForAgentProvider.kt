package com.glassthought.shepherd.core.context

import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import java.nio.file.Path

/**
 * Assembles instruction files for agents.
 *
 * Each agent receives a single Markdown file containing everything it needs — role definition,
 * ticket, shared context, prior agent outputs, and communication tooling. The provider is the
 * **single place** that decides what each agent sees.
 *
 * Single public method: `assembleInstructions(request)`. The request type encodes the role —
 * no separate role parameter needed. Invalid field combinations are caught at compile time,
 * not at runtime. Adding a new agent role requires only a new `AgentInstructionRequest`
 * subtype and a plan list.
 *
 * ap.9HksYVzl1KkR9E1L2x8Tx.E
 *
 * @see InstructionText for static text constants
 * @see InstructionSection for section subtypes and their rendering logic
 * @see InstructionPlanAssembler for the rendering engine
 * @see ProtocolVocabulary for protocol keyword constants
 */
fun interface ContextForAgentProvider {

    /**
     * Assembles the instruction file for an agent.
     * The request type encodes the role — no separate role parameter needed.
     * Writes to `request.outputDir/instructions.md`. Returns the written path.
     */
    suspend fun assembleInstructions(request: AgentInstructionRequest): Path

    companion object {
        fun standard(outFactory: OutFactory, aiOutputStructure: AiOutputStructure): ContextForAgentProvider =
            ContextForAgentProviderImpl(
                outFactory = outFactory,
                assembler = InstructionPlanAssembler(outFactory),
                aiOutputStructure = aiOutputStructure,
            )
    }
}

/**
 * Shared fields between [AgentInstructionRequest.DoerRequest] and
 * [AgentInstructionRequest.ReviewerRequest] — extracted via composition instead of an
 * intermediate sealed class that would require override boilerplate and nested when-matching.
 */
data class ExecutionContext(
    val partName: String,
    val partDescription: String,
    val planMdPath: Path?,               // null -> no-planning workflow
    val priorPublicMdPaths: List<Path>,
)

/**
 * Sealed request hierarchy for agent instruction assembly.
 *
 * Each role gets its own subtype with exactly the fields it needs. No nullable role-specific
 * fields; the type itself is the discriminator. Remaining nullables are semantically meaningful
 * optionals (absent on first iteration or absent in no-planning workflows).
 */
sealed class AgentInstructionRequest {
    // -- common (all roles) --
    abstract val roleDefinition: RoleDefinition
    abstract val ticketContent: String
    abstract val iterationNumber: Int
    abstract val outputDir: Path
    abstract val publicMdOutputPath: Path
    /** Explicit path to PRIVATE.md from a prior session. Null means no prior context to inject. */
    abstract val privateMdPath: Path?

    data class DoerRequest(
        override val roleDefinition: RoleDefinition,
        override val ticketContent: String,
        override val iterationNumber: Int,
        override val outputDir: Path,
        override val publicMdOutputPath: Path,
        override val privateMdPath: Path? = null,
        val executionContext: ExecutionContext,
        val reviewerPublicMdPath: Path?,     // null on iteration 1
    ) : AgentInstructionRequest()

    /**
     * Request for a doer processing a single feedback item in the inner feedback loop.
     *
     * Distinct from [DoerRequest] because the doer receives per-item feedback content
     * (via [feedbackItem]) instead of the reviewer's overall PUBLIC.md (via
     * [DoerRequest.reviewerPublicMdPath]).
     *
     * See granular-feedback-loop spec (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).
     */
    data class DoerFeedbackItemRequest(
        override val roleDefinition: RoleDefinition,
        override val ticketContent: String,
        override val iterationNumber: Int,
        override val outputDir: Path,
        override val publicMdOutputPath: Path,
        override val privateMdPath: Path? = null,
        val executionContext: ExecutionContext,
        val feedbackItem: InstructionSection.FeedbackItem,
    ) : AgentInstructionRequest()

    data class ReviewerRequest(
        override val roleDefinition: RoleDefinition,
        override val ticketContent: String,
        override val iterationNumber: Int,
        override val outputDir: Path,
        override val publicMdOutputPath: Path,
        override val privateMdPath: Path? = null,
        val executionContext: ExecutionContext,
        val doerPublicMdPath: Path,          // always required; non-nullable
        val feedbackDir: Path,               // always required; non-nullable
    ) : AgentInstructionRequest()

    data class PlannerRequest(
        override val roleDefinition: RoleDefinition,
        override val ticketContent: String,
        override val iterationNumber: Int,
        override val outputDir: Path,
        override val publicMdOutputPath: Path,
        override val privateMdPath: Path? = null,
        val roleCatalogEntries: List<RoleCatalogEntry>,
        val planReviewerPublicMdPath: Path?,     // null on iteration 1
        val planJsonOutputPath: Path,            // always required; non-nullable
        val planMdOutputPath: Path,              // always required; non-nullable
    ) : AgentInstructionRequest()

    data class PlanReviewerRequest(
        override val roleDefinition: RoleDefinition,
        override val ticketContent: String,
        override val iterationNumber: Int,
        override val outputDir: Path,
        override val publicMdOutputPath: Path,
        override val privateMdPath: Path? = null,
        val planJsonContent: String,             // always required; non-nullable
        val planMdContent: String,               // always required; non-nullable
        val plannerPublicMdPath: Path,           // always required; non-nullable
        val priorPlanReviewerPublicMdPath: Path?, // null on iteration 1
    ) : AgentInstructionRequest()
}

/**
 * Minimal role info for the planner's role catalog section.
 *
 * A top-level data model type — lightweight projection of
 * [com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition] containing only
 * the fields the planner needs.
 */
data class RoleCatalogEntry(
    val name: String,
    val description: String,
    val descriptionLong: String?,
)
