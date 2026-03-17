package com.glassthought.shepherd.core.context

import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import java.nio.file.Path

/**
 * Assembles instruction files for agents.
 *
 * Each agent receives a single Markdown file containing everything it needs — role definition,
 * ticket, shared context, prior agent outputs, and communication tooling. The provider is the
 * **single place** that decides what each agent sees.
 *
 * Four methods — one per role. Each agent type has different content needs, and the type system
 * makes that explicit. No `when(agentKind)` branching inside a single method, no boolean flags.
 *
 * ap.9HksYVzl1KkR9E1L2x8Tx.E
 *
 * @see InstructionSections for all static text sections
 * @see ProtocolVocabulary for protocol keyword constants
 */
interface ContextForAgentProvider {

    /**
     * Assembles instruction file for a doer execution agent.
     * Writes to the sub-part's `comm/in/instructions.md` in `.ai_out/`.
     * Returns the path to the written file.
     */
    suspend fun assembleDoerInstructions(request: DoerInstructionRequest): Path

    /**
     * Assembles instruction file for a reviewer execution agent.
     * Writes to the sub-part's `comm/in/instructions.md` in `.ai_out/`.
     * Returns the path to the written file.
     */
    suspend fun assembleReviewerInstructions(request: ReviewerInstructionRequest): Path

    /**
     * Assembles instruction file for the PLANNER agent.
     * Writes to the sub-part's `comm/in/instructions.md` in `.ai_out/`.
     * Returns the path to the written file.
     */
    suspend fun assemblePlannerInstructions(request: PlannerInstructionRequest): Path

    /**
     * Assembles instruction file for the PLAN_REVIEWER agent.
     * Writes to the sub-part's `comm/in/instructions.md` in `.ai_out/`.
     * Returns the path to the written file.
     */
    suspend fun assemblePlanReviewerInstructions(request: PlanReviewerInstructionRequest): Path

    companion object {
        fun standard(outFactory: OutFactory): ContextForAgentProvider =
            ContextForAgentProviderImpl(outFactory)
    }
}

/**
 * Request for assembling doer execution agent instructions.
 *
 * @param roleDefinition The agent's role definition (loaded from catalog)
 * @param partName Name of the current part in the workflow
 * @param partDescription Description of what this part accomplishes
 * @param ticketContent Full ticket markdown content (including frontmatter)
 * @param sharedContextPath Path to `.ai_out/${branch}/shared/SHARED_CONTEXT.md`
 * @param planMdPath Path to `shared/plan/PLAN.md` — null for no-planning workflows
 * @param priorPublicMdPaths Paths to all visible prior PUBLIC.md files (completed parts + peer)
 * @param iterationNumber Current iteration (1-based)
 * @param reviewerPublicMdPath Path to reviewer's PUBLIC.md from prior iteration — null on iteration 1
 * @param outputDir Directory where `instructions.md` will be written
 * @param publicMdOutputPath Where the agent should write its PUBLIC.md
 */
data class DoerInstructionRequest(
    val roleDefinition: RoleDefinition,
    val partName: String,
    val partDescription: String,
    val ticketContent: String,
    val sharedContextPath: Path,
    val planMdPath: Path?,
    val priorPublicMdPaths: List<Path>,
    val iterationNumber: Int,
    val reviewerPublicMdPath: Path?,
    val outputDir: Path,
    val publicMdOutputPath: Path,
)

/**
 * Request for assembling reviewer execution agent instructions.
 *
 * @param roleDefinition The agent's role definition (loaded from catalog)
 * @param partName Name of the current part in the workflow
 * @param partDescription Description of what this part accomplishes
 * @param ticketContent Full ticket markdown content (including frontmatter)
 * @param sharedContextPath Path to `.ai_out/${branch}/shared/SHARED_CONTEXT.md`
 * @param planMdPath Path to `shared/plan/PLAN.md` — null for no-planning workflows
 * @param priorPublicMdPaths Paths to all visible prior PUBLIC.md files (completed parts + peer)
 * @param iterationNumber Current iteration (1-based)
 * @param doerPublicMdPath Path to doer's PUBLIC.md for this part — null if doer hasn't run yet
 * @param feedbackDir Path to `__feedback/` directory at part level (for iteration > 1)
 * @param outputDir Directory where `instructions.md` will be written
 * @param publicMdOutputPath Where the agent should write its PUBLIC.md
 */
data class ReviewerInstructionRequest(
    val roleDefinition: RoleDefinition,
    val partName: String,
    val partDescription: String,
    val ticketContent: String,
    val sharedContextPath: Path,
    val planMdPath: Path?,
    val priorPublicMdPaths: List<Path>,
    val iterationNumber: Int,
    val doerPublicMdPath: Path?,
    val feedbackDir: Path?,
    val outputDir: Path,
    val publicMdOutputPath: Path,
)

/**
 * Request for assembling planner instructions.
 *
 * @param roleDefinition The PLANNER role definition
 * @param ticketContent Full ticket markdown content
 * @param sharedContextPath Path to SHARED_CONTEXT.md
 * @param roleCatalogEntries All available roles (for assignment)
 * @param iterationNumber Current iteration (1-based)
 * @param planReviewerPublicMdPath Plan reviewer's PUBLIC.md from prior iteration (null on first)
 * @param planJsonOutputPath Where the planner writes plan.json
 * @param planMdOutputPath Where the planner writes PLAN.md
 * @param outputDir Directory where instructions.md will be written
 * @param publicMdOutputPath Where the planner writes PUBLIC.md
 */
data class PlannerInstructionRequest(
    val roleDefinition: RoleDefinition,
    val ticketContent: String,
    val sharedContextPath: Path,
    val roleCatalogEntries: List<InstructionSections.RoleCatalogEntry>,
    val iterationNumber: Int,
    val planReviewerPublicMdPath: Path?,
    val planJsonOutputPath: Path,
    val planMdOutputPath: Path,
    val outputDir: Path,
    val publicMdOutputPath: Path,
)

/**
 * Request for assembling plan reviewer instructions.
 *
 * @param roleDefinition The PLAN_REVIEWER role definition
 * @param ticketContent Full ticket markdown content
 * @param planJsonContent Content of plan.json (read by caller — not a path)
 * @param planMdContent Content of PLAN.md (read by caller — not a path)
 * @param plannerPublicMdPath Path to planner's PUBLIC.md
 * @param iterationNumber Current iteration (1-based)
 * @param priorPlanReviewerPublicMdPath Plan reviewer's own prior PUBLIC.md (null on first)
 * @param outputDir Directory where instructions.md will be written
 * @param publicMdOutputPath Where the plan reviewer writes PUBLIC.md
 */
data class PlanReviewerInstructionRequest(
    val roleDefinition: RoleDefinition,
    val ticketContent: String,
    val planJsonContent: String,
    val planMdContent: String,
    val plannerPublicMdPath: Path,
    val iterationNumber: Int,
    val priorPlanReviewerPublicMdPath: Path?,
    val outputDir: Path,
    val publicMdOutputPath: Path,
)
