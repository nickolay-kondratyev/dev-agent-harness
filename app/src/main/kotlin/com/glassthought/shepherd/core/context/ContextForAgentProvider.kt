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
 * Single public method: `assembleInstructions(role, request)`. The caller passes `role` — the
 * provider dispatches to the correct internal section plan. Adding a new agent role requires only
 * a new `AgentRole` variant and a plan list, not a new interface method + request type.
 *
 * ap.9HksYVzl1KkR9E1L2x8Tx.E
 *
 * @see InstructionSections for all static text sections
 * @see ProtocolVocabulary for protocol keyword constants
 */
interface ContextForAgentProvider {

    /**
     * Assembles the instruction file for an agent.
     * `role` selects the section plan; `request` supplies all data needed by any role.
     * Writes to `request.outputDir/instructions.md`. Returns the written path.
     */
    suspend fun assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path

    companion object {
        fun standard(outFactory: OutFactory): ContextForAgentProvider =
            ContextForAgentProviderImpl(outFactory)
    }
}

/** Discriminates which agent role is being assembled for. Drives section plan selection. */
enum class AgentRole { DOER, REVIEWER, PLANNER, PLAN_REVIEWER }

/**
 * Single request type for all agent roles.
 *
 * Common fields are required for all roles. Role-specific fields are nullable (or empty list)
 * when not applicable. The `role` parameter passed to `assembleInstructions` is the discriminator
 * — the provider accesses only the fields relevant to the given role.
 *
 * Field grouping comments document which roles use each field.
 */
data class UnifiedInstructionRequest(
    // ── common (all roles) ──────────────────────────────────────────────────
    val roleDefinition: RoleDefinition,
    val ticketContent: String,
    val iterationNumber: Int,
    val outputDir: Path,
    val publicMdOutputPath: Path,

    // ── execution agents (DOER + REVIEWER) ──────────────────────────────────
    val partName: String? = null,
    val partDescription: String? = null,
    val planMdPath: Path? = null,              // null → no-planning workflow
    val priorPublicMdPaths: List<Path> = emptyList(),

    // ── DOER-only ───────────────────────────────────────────────────────────
    val reviewerPublicMdPath: Path? = null,    // null on iteration 1

    // ── REVIEWER-only ───────────────────────────────────────────────────────
    val doerPublicMdPath: Path? = null,
    val feedbackDir: Path? = null,

    // ── PLANNER-only ────────────────────────────────────────────────────────
    val roleCatalogEntries: List<RoleCatalogEntry> = emptyList(),
    val planReviewerPublicMdPath: Path? = null, // null on iteration 1
    val planJsonOutputPath: Path? = null,
    val planMdOutputPath: Path? = null,

    // ── PLAN_REVIEWER-only ──────────────────────────────────────────────────
    val planJsonContent: String? = null,
    val planMdContent: String? = null,
    val plannerPublicMdPath: Path? = null,
    val priorPlanReviewerPublicMdPath: Path? = null, // null on iteration 1
)

/**
 * Minimal role info for the planner's role catalog section.
 *
 * A top-level data model type — independent of [InstructionSections] (the rendering layer).
 * Avoids coupling to [com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition] which
 * carries heavy implementation details the planner does not need.
 */
data class RoleCatalogEntry(
    val name: String,
    val description: String,
    val descriptionLong: String?,
)
