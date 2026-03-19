package com.glassthought.shepherd.core.context

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
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
 * **7 shared subtypes** (1–7) plus **7 role-specific subtypes** (8–14) for execution
 * agents (doer/reviewer) and planner agents.
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

    // ── 8. Plan (PLAN.md) — execution agents only ─────────────────────────

    /**
     * Renders the human-readable plan for execution agents (doer/reviewer).
     *
     * Reads from [ExecutionContext.planMdPath]:
     * - `null` (no-planning workflow) → returns `null` (skip).
     * - Non-null → reads the file and renders under `# Plan` heading.
     *   The file MUST exist — throws [IllegalStateException] if missing.
     *
     * Returns `null` for planner/plan-reviewer requests (they produce the plan, not consume it).
     */
    data object PlanMd : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? {
            val planMdPath = when (request) {
                is AgentInstructionRequest.DoerRequest -> request.executionContext.planMdPath
                is AgentInstructionRequest.ReviewerRequest -> request.executionContext.planMdPath
                is AgentInstructionRequest.PlannerRequest -> null
                is AgentInstructionRequest.PlanReviewerRequest -> null
            } ?: return null

            check(Files.exists(planMdPath)) {
                "Plan file must exist at [$planMdPath] but was not found"
            }
            return "# Plan\n\n${planMdPath.readText()}"
        }
    }

    // ── 9. Prior PUBLIC.md files — execution agents only ──────────────────

    /**
     * Renders prior agents' PUBLIC.md files for execution agents (doer/reviewer).
     *
     * Reads from [ExecutionContext.priorPublicMdPaths]:
     * - Empty list → returns `null` (skip).
     * - Non-empty → renders each file under a heading with its filename.
     *   Each file MUST exist — throws [IllegalStateException] if missing
     *   (upstream guarantees existence).
     *
     * Returns `null` for planner/plan-reviewer requests.
     */
    data object PriorPublicMd : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? {
            val paths = when (request) {
                is AgentInstructionRequest.DoerRequest -> request.executionContext.priorPublicMdPaths
                is AgentInstructionRequest.ReviewerRequest -> request.executionContext.priorPublicMdPaths
                is AgentInstructionRequest.PlannerRequest -> null
                is AgentInstructionRequest.PlanReviewerRequest -> null
            }?.takeIf { it.isNotEmpty() } ?: return null

            return paths.joinToString("\n\n") { path ->
                check(Files.exists(path)) {
                    "Prior PUBLIC.md must exist at [$path] but was not found"
                }
                "## ${path.name}\n\n${path.readText()}"
            }
        }
    }

    // ── 10. Iteration feedback — doer only ────────────────────────────────

    /**
     * Renders reviewer feedback for a doer on iteration > 1.
     *
     * Only applies to [AgentInstructionRequest.DoerRequest]:
     * - [DoerRequest.reviewerPublicMdPath] `null` (iteration 1) → returns `null`.
     * - Non-null → renders the reviewer's PUBLIC.md content under a heading, then appends
     *   [InstructionText.DOER_PUSHBACK_GUIDANCE] wrapped in compaction-survival tags so
     *   pushback guidance survives context window compaction.
     *
     * Returns `null` for all other request types.
     */
    data object IterationFeedback : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? {
            val reviewerPath = (request as? AgentInstructionRequest.DoerRequest)
                ?.reviewerPublicMdPath ?: return null

            check(Files.exists(reviewerPath)) {
                "Reviewer PUBLIC.md must exist at [$reviewerPath] but was not found"
            }

            return buildString {
                appendLine("## Reviewer Feedback")
                appendLine()
                appendLine(reviewerPath.readText())
                appendLine()
                appendLine("<critical_to_keep_through_compaction>")
                appendLine(InstructionText.DOER_PUSHBACK_GUIDANCE)
                appendLine("</critical_to_keep_through_compaction>")
            }.trimEnd()
        }
    }

    // ── 11. Inline file content (generic) ─────────────────────────────────

    /**
     * Reads a file at [path] and renders its content under the given [heading].
     *
     * - [path] `null` → returns `null` (skip silently).
     * - [path] non-null, file missing → throws [IllegalStateException] (fail hard).
     * - [path] non-null, file exists → renders as `## $heading\n\n<content>`.
     */
    data class InlineFileContentSection(
        val heading: String,
        val path: Path?,
    ) : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? {
            val filePath = path ?: return null
            check(Files.exists(filePath)) {
                "File for section [$heading] must exist at [$filePath] but was not found"
            }
            return "## $heading\n\n${filePath.readText()}"
        }
    }

    // ── 12. Role catalog — planner only ───────────────────────────────────

    /**
     * Renders the available roles catalog for the planner.
     *
     * Only applies to [AgentInstructionRequest.PlannerRequest] — returns `null` for all others.
     * Delegates to [InstructionRenderers.roleCatalog].
     */
    data object RoleCatalog : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? {
            if (request !is AgentInstructionRequest.PlannerRequest) return null
            return InstructionRenderers.roleCatalog(request.roleCatalogEntries)
        }
    }

    // ── 13. Available agent types & models ────────────────────────────────

    /**
     * Returns static [InstructionText.AGENT_TYPES_AND_MODELS].
     *
     * Available for any request type — used by planner and plan reviewer.
     */
    data object AvailableAgentTypes : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String =
            InstructionText.AGENT_TYPES_AND_MODELS
    }

    // ── 14. Plan format instructions — planner only ───────────────────────

    /**
     * Returns static [InstructionText.PLAN_FORMAT_INSTRUCTIONS].
     *
     * Only applies to [AgentInstructionRequest.PlannerRequest] — returns `null` for all others.
     */
    data object PlanFormatInstructions : InstructionSection() {
        override fun render(request: AgentInstructionRequest): String? {
            if (request !is AgentInstructionRequest.PlannerRequest) return null
            return InstructionText.PLAN_FORMAT_INSTRUCTIONS
        }
    }
}
