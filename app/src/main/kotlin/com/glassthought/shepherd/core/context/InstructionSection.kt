package com.glassthought.shepherd.core.context

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Sealed hierarchy of instruction sections that can appear in an agent's instruction file.
 *
 * Each subtype knows how to render itself given an [AgentInstructionRequest]. The sealed
 * class enables compile-time exhaustiveness checks when building per-role instruction plans.
 *
 * A `render` call returns `null` to signal "skip this section" — the assembler filters
 * out nulls before joining, avoiding stray separators for absent optional sections.
 *
 * **7 shared subtypes** (this ticket); role-specific subtypes (InlineFileContentSection,
 * FeedbackDirectorySection) are tracked separately.
 *
 * See ContextForAgentProvider spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E), "Internal Design:
 * Data-Driven Assembly" section.
 *
 * ap.YkR8mNv3pLwQ2xJtF5dZs.E
 */
sealed class InstructionSection {

    /**
     * Renders this section for the given [request].
     *
     * @return the rendered Markdown string, or `null` if this section should be skipped
     *         (e.g. PrivateMd when no prior session file exists).
     */
    abstract fun render(request: AgentInstructionRequest): String?

    // ── 1. Role definition ───────────────────────────────────────────────────

    /**
     * Renders the role heading and the full role definition file content.
     *
     * Reads from [AgentInstructionRequest.roleDefinition].
     */
    data object RoleDefinition : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String =
            "# Role: ${request.roleDefinition.name}\n\n${request.roleDefinition.filePath.readText()}"
    }

    // ── 2. Prior session context (PRIVATE.md) ────────────────────────────────

    /**
     * Renders prior session context from PRIVATE.md.
     *
     * Returns `null` (skip) when:
     * - [AgentInstructionRequest.privateMdPath] is null (no prior session)
     * - The file does not exist on disk
     * - The file is blank (empty or whitespace-only)
     *
     * See self-compaction spec (ref.ap.8nwz2AHf503xwq8fKuLcl.E).
     */
    data object PrivateMd : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? =
            request.privateMdPath
                ?.takeIf { Files.exists(it) }
                ?.readText()
                ?.takeIf { it.isNotBlank() }
                ?.let { "# Prior Session Context (PRIVATE.md)\n\n$it" }
    }

    // ── 3. Part context ──────────────────────────────────────────────────────

    /**
     * Renders part name and description for execution-phase agents (doer/reviewer).
     *
     * Returns `null` for planner/plan-reviewer requests which do not have an execution context.
     * Delegates to [InstructionRenderers.partContext].
     */
    data object PartContext : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? {
            val executionContext = when (request) {
                is AgentInstructionRequest.DoerRequest -> request.executionContext
                is AgentInstructionRequest.ReviewerRequest -> request.executionContext
                is AgentInstructionRequest.PlannerRequest -> null
                is AgentInstructionRequest.PlanReviewerRequest -> null
            }
            return executionContext?.let {
                InstructionRenderers.partContext(it.partName, it.partDescription)
            }
        }
    }

    // ── 4. Ticket ────────────────────────────────────────────────────────────

    /**
     * Renders the ticket content under a `# Ticket` heading.
     */
    data object Ticket : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String =
            "# Ticket\n\n${request.ticketContent}"
    }

    // ── 5. Output path (generic, labeled) ────────────────────────────────────

    /**
     * Renders a labeled output path section telling the agent where to write a specific file.
     *
     * Constructed with a concrete [label] and [path] when building the per-request plan.
     * Replaces the former role-specific output path sections (PlanFlowJsonOutputPath,
     * PlanMdOutputPath, PublicMdOutputPath) which each had slightly different heading/body
     * wording (e.g. "Output Path" vs "$label Output Path", "human-readable plan" vs file name).
     * Those differences were accidental inconsistency. This class provides a **single canonical
     * template** for all output paths: heading `## $label Output Path` and body
     * `Write your $label to:`. This is the intended format going forward.
     */
    data class OutputPathSection(
        val label: String,
        val path: Path,
    ) : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String = """
            ## $label Output Path

            Write your $label to:
            `$path`
        """.trimIndent()
    }

    // ── 6. PUBLIC.md writing guidelines ──────────────────────────────────────

    /**
     * Returns the static PUBLIC.md writing guidelines text.
     */
    data object WritingGuidelines : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String =
            InstructionText.PUBLIC_MD_WRITING_GUIDELINES
    }

    // ── 7. Callback script usage ─────────────────────────────────────────────

    /**
     * Renders the callback script usage block, parameterized by role.
     *
     * @param forReviewer controls which done-result values are shown (pass/needs_iteration vs completed).
     * @param includePlanValidation adds the `validate-plan` query section for planning-phase agents.
     */
    data class CallbackHelp(
        val forReviewer: Boolean,
        val includePlanValidation: Boolean,
    ) : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String =
            InstructionRenderers.callbackScriptUsage(
                forReviewer = forReviewer,
                includePlanValidation = includePlanValidation,
            )
    }
}
