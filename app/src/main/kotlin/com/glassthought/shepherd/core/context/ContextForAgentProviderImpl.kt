package com.glassthought.shepherd.core.context

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Default implementation of [ContextForAgentProvider].
 *
 * Each `assemble*()` method delegates to a private `build*Sections()` template method. Each
 * template uses `buildList` to show the concatenation order as a readable linear sequence — no
 * role-dispatching conditionals. The `buildList` IS the documentation of the concatenation order.
 *
 * See ContextForAgentProvider.md (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for the authoritative
 * concatenation tables.
 */
class ContextForAgentProviderImpl(outFactory: OutFactory) : ContextForAgentProvider {

    private val out = outFactory.getOutForClass(ContextForAgentProviderImpl::class)

    // ── Doer ──────────────────────────────────────────────────────────────────

    override suspend fun assembleDoerInstructions(
        request: DoerInstructionRequest,
    ): Path {
        out.debug("assembling_doer_instructions") {
            listOf(
                Val(request.partName, ValType.STRING_USER_AGNOSTIC),
                Val(request.iterationNumber.toString(), ValType.STRING_USER_AGNOSTIC),
            )
        }

        val sections = buildDoerSections(request)
        return writeInstructionsFile(request.outputDir, sections)
    }

    private fun buildDoerSections(request: DoerInstructionRequest): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. Part context
        add(InstructionRenderers.partContext(request.partName, request.partDescription))
        // 3. Ticket
        add(ticketSection(request.ticketContent))
        // 4. PLAN.md (with-planning only)
        request.planMdPath?.let { add(planSection(it)) }
        // 5. Prior PUBLIC.md files
        addAll(priorPublicMdSections(request.priorPublicMdPaths))
        // 7. Iteration feedback (iteration > 1): reviewer's PUBLIC.md + pushback guidance
        if (request.iterationNumber > 1 && request.reviewerPublicMdPath != null) {
            add(reviewerFeedbackForDoerSection(request.reviewerPublicMdPath))
            add(InstructionText.DOER_PUSHBACK_GUIDANCE)
        }
        // 8. Output paths
        add(InstructionRenderers.publicMdOutputPath(request.publicMdOutputPath))
        // 9. PUBLIC.md writing guidelines
        add(InstructionText.PUBLIC_MD_WRITING_GUIDELINES)
        // 10. Callback script usage
        add(
            InstructionRenderers.callbackScriptUsage(
                forReviewer = false,
                includePlanValidation = false,
            )
        )
    }

    // ── Reviewer ──────────────────────────────────────────────────────────────

    override suspend fun assembleReviewerInstructions(
        request: ReviewerInstructionRequest,
    ): Path {
        out.debug("assembling_reviewer_instructions") {
            listOf(
                Val(request.partName, ValType.STRING_USER_AGNOSTIC),
                Val(request.iterationNumber.toString(), ValType.STRING_USER_AGNOSTIC),
            )
        }

        val sections = buildReviewerSections(request)
        return writeInstructionsFile(request.outputDir, sections)
    }

    private fun buildReviewerSections(request: ReviewerInstructionRequest): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. Part context
        add(InstructionRenderers.partContext(request.partName, request.partDescription))
        // 3. Ticket
        add(ticketSection(request.ticketContent))
        // 4. PLAN.md (with-planning only)
        request.planMdPath?.let { add(planSection(it)) }
        // 5. Prior PUBLIC.md files
        addAll(priorPublicMdSections(request.priorPublicMdPaths))
        // 6. Doer's PUBLIC.md for review
        request.doerPublicMdPath?.let { add(doerOutputForReviewerSection(it)) }
        // 6a. Structured feedback format
        add(InstructionText.REVIEWER_FEEDBACK_FORMAT)
        // 6b–6d. Feedback state (iteration > 1)
        if (request.iterationNumber > 1 && request.feedbackDir != null) {
            addAll(feedbackStateSections(request.feedbackDir))
        }
        // 6e. Feedback writing instructions
        add(InstructionText.FEEDBACK_WRITING_INSTRUCTIONS)
        // 7. Output paths
        add(InstructionRenderers.publicMdOutputPath(request.publicMdOutputPath))
        // 8. PUBLIC.md writing guidelines
        add(InstructionText.PUBLIC_MD_WRITING_GUIDELINES)
        // 9. Callback script usage
        add(
            InstructionRenderers.callbackScriptUsage(
                forReviewer = true,
                includePlanValidation = false,
            )
        )
    }

    // ── Planner ───────────────────────────────────────────────────────────────

    override suspend fun assemblePlannerInstructions(
        request: PlannerInstructionRequest,
    ): Path {
        out.debug("assembling_planner_instructions") {
            listOf(Val(request.iterationNumber.toString(), ValType.STRING_USER_AGNOSTIC))
        }

        val sections = buildPlannerSections(request)
        return writeInstructionsFile(request.outputDir, sections)
    }

    private fun buildPlannerSections(request: PlannerInstructionRequest): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. Ticket
        add(ticketSection(request.ticketContent))
        // 3. Role catalog
        add(InstructionRenderers.roleCatalog(request.roleCatalogEntries))
        // 4. Available agent types & models
        add(InstructionText.AGENT_TYPES_AND_MODELS)
        // 5. Plan format instructions
        add(InstructionText.PLAN_FORMAT_INSTRUCTIONS)
        // 6. Reviewer feedback (iteration > 1)
        if (request.iterationNumber > 1 && request.planReviewerPublicMdPath != null) {
            add(reviewerFeedbackForDoerSection(request.planReviewerPublicMdPath))
        }
        // 7. plan.json output path
        add(planJsonOutputPathSection(request.planJsonOutputPath))
        // 8. PLAN.md output path
        add(planMdOutputPathSection(request.planMdOutputPath))
        // 9. PUBLIC.md output path
        add(InstructionRenderers.publicMdOutputPath(request.publicMdOutputPath))
        // 10. PUBLIC.md writing guidelines
        add(InstructionText.PUBLIC_MD_WRITING_GUIDELINES)
        // 11. Callback script usage (includes validate-plan query)
        add(
            InstructionRenderers.callbackScriptUsage(
                forReviewer = false,
                includePlanValidation = true,
            )
        )
    }

    // ── Plan Reviewer ─────────────────────────────────────────────────────────

    override suspend fun assemblePlanReviewerInstructions(
        request: PlanReviewerInstructionRequest,
    ): Path {
        out.debug("assembling_plan_reviewer_instructions") {
            listOf(Val(request.iterationNumber.toString(), ValType.STRING_USER_AGNOSTIC))
        }

        val sections = buildPlanReviewerSections(request)
        return writeInstructionsFile(request.outputDir, sections)
    }

    private fun buildPlanReviewerSections(request: PlanReviewerInstructionRequest): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. Ticket
        add(ticketSection(request.ticketContent))
        // 3. plan.json content
        add(planJsonContentSection(request.planJsonContent))
        // 4. PLAN.md content
        add(planMdContentSection(request.planMdContent))
        // 5. Available agent types & models
        add(InstructionText.AGENT_TYPES_AND_MODELS)
        // 6. Planner's PUBLIC.md
        add(plannerPublicMdSection(request.plannerPublicMdPath))
        // 7. Iteration feedback (iteration > 1)
        if (request.iterationNumber > 1 && request.priorPlanReviewerPublicMdPath != null) {
            add(priorPlanReviewerFeedbackSection(request.priorPlanReviewerPublicMdPath))
        }
        // 8. PUBLIC.md output path
        add(InstructionRenderers.publicMdOutputPath(request.publicMdOutputPath))
        // 9. PUBLIC.md writing guidelines
        add(InstructionText.PUBLIC_MD_WRITING_GUIDELINES)
        // 10. Callback script usage (includes validate-plan query)
        add(
            InstructionRenderers.callbackScriptUsage(
                forReviewer = true,
                includePlanValidation = true,
            )
        )
    }

    // ── Per-section private methods ──────────────────────────────────────────
    // Each reads a file or returns formatted text. Named to match the spec's section names.

    private fun roleDefinitionSection(role: RoleDefinition): String =
        "# Role: ${role.name}\n\n${role.filePath.readText()}"

    private fun ticketSection(content: String): String =
        "# Ticket\n\n$content"

    private fun planSection(planMdPath: Path): String =
        "# Plan\n\n${planMdPath.readText()}"

    private fun priorPublicMdSections(paths: List<Path>): List<String> =
        if (paths.isEmpty()) {
            emptyList()
        } else {
            listOf(
                buildString {
                    appendLine("# Prior Agent Outputs")
                    appendLine()
                    paths.forEachIndexed { index, path ->
                        appendLine("## Prior Output ${index + 1}: ${path.parent.parent.fileName}")
                        appendLine()
                        appendLine(path.readText())
                        appendLine()
                    }
                }
            )
        }

    private fun doerOutputForReviewerSection(doerPublicMdPath: Path): String =
        "# Doer Output (for review)\n\n${doerPublicMdPath.readText()}"

    private fun reviewerFeedbackForDoerSection(reviewerPublicMdPath: Path): String =
        "# Reviewer Feedback\n\n${reviewerPublicMdPath.readText()}"

    private fun feedbackStateSections(feedbackDir: Path): List<String> = buildList {
        // 7a. Addressed feedback
        val addressed = collectFeedbackFiles(feedbackDir, ProtocolVocabulary.FeedbackStatus.ADDRESSED)
        if (addressed.isNotEmpty()) {
            add(InstructionText.ADDRESSED_FEEDBACK_HEADER + "\n\n" + addressed)
        }
        // 7b. Rejected feedback
        val rejected = collectFeedbackFiles(feedbackDir, ProtocolVocabulary.FeedbackStatus.REJECTED)
        if (rejected.isNotEmpty()) {
            add(InstructionText.REJECTED_FEEDBACK_HEADER + "\n\n" + rejected)
        }
        // 7c. Skipped optional
        val skippedOptional = collectFeedbackFilesInDir(
            feedbackDir
                .resolve(ProtocolVocabulary.FeedbackStatus.UNADDRESSED)
                .resolve(ProtocolVocabulary.Severity.OPTIONAL)
        )
        if (skippedOptional.isNotEmpty()) {
            add(InstructionText.SKIPPED_OPTIONAL_HEADER + "\n\n" + skippedOptional)
        }
    }

    /**
     * Collects all feedback files under `feedbackDir/{status}/` across all severity levels.
     */
    private fun collectFeedbackFiles(feedbackDir: Path, status: String): String {
        val severities = listOf(
            ProtocolVocabulary.Severity.CRITICAL,
            ProtocolVocabulary.Severity.IMPORTANT,
            ProtocolVocabulary.Severity.OPTIONAL,
        )
        return severities
            .map { severity -> feedbackDir.resolve(status).resolve(severity) }
            .flatMap { dir -> collectMarkdownFilesInDir(dir) }
            .joinToString("\n\n---\n\n")
    }

    /**
     * Reads all `.md` files in a directory and returns their content concatenated.
     */
    private fun collectFeedbackFilesInDir(dir: Path): String =
        collectMarkdownFilesInDir(dir).joinToString("\n\n---\n\n")

    private fun collectMarkdownFilesInDir(dir: Path): List<String> =
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            Files.list(dir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                    .sorted()
                    .map { "### ${it.fileName}\n\n${it.readText()}" }
                    .toList()
            }
        } else {
            emptyList()
        }

    // ── Planner-specific sections ────────────────────────────────────────────

    private fun planJsonOutputPathSection(path: Path): String = """
        ## plan.json Output Path

        Write your plan.json to:
        `$path`
    """.trimIndent()

    private fun planMdOutputPathSection(path: Path): String = """
        ## PLAN.md Output Path

        Write your human-readable plan to:
        `$path`
    """.trimIndent()

    private fun planJsonContentSection(content: String): String =
        "# plan.json\n\n```json\n$content\n```"

    private fun planMdContentSection(content: String): String =
        "# PLAN.md\n\n$content"

    private fun plannerPublicMdSection(path: Path): String =
        "# Planner's Rationale\n\n${path.readText()}"

    private fun priorPlanReviewerFeedbackSection(path: Path): String =
        "# Your Prior Feedback\n\n${path.readText()}"

    // ── File writing ─────────────────────────────────────────────────────────

    private suspend fun writeInstructionsFile(outputDir: Path, sections: List<String>): Path =
        withContext(Dispatchers.IO) {
            Files.createDirectories(outputDir)
            val instructionsPath = outputDir.resolve("instructions.md")
            instructionsPath.toFile().writeText(sections.joinToString("\n\n---\n\n"))

            out.info(
                "instructions_file_written",
                Val(instructionsPath.toString(), ValType.FILE_PATH_STRING),
            )

            instructionsPath
        }
}
