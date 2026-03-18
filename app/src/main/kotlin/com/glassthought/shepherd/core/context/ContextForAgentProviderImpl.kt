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
 * `assembleInstructions` dispatches on the sealed [AgentInstructionRequest] type to pick the
 * correct internal section plan. Each private `build*Sections()` method uses `buildList` to show
 * the concatenation order as a readable linear sequence — no scattered role-dispatching
 * conditionals. The `buildList` IS the documentation of the concatenation order.
 *
 * See ContextForAgentProvider.md (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for the authoritative
 * concatenation tables.
 */
class ContextForAgentProviderImpl(outFactory: OutFactory) : ContextForAgentProvider {

    private val out = outFactory.getOutForClass(ContextForAgentProviderImpl::class)

    override suspend fun assembleInstructions(
        request: AgentInstructionRequest,
    ): Path {
        out.debug("assembling_instructions") {
            buildList {
                add(Val(request::class.simpleName ?: "Unknown", ValType.STRING_USER_AGNOSTIC))
                add(Val(request.iterationNumber.toString(), ValType.STRING_USER_AGNOSTIC))
                request.executionContextOrNull?.partName?.let {
                    add(Val(it, ValType.STRING_USER_AGNOSTIC))
                }
            }
        }

        val sections = when (request) {
            is AgentInstructionRequest.DoerRequest -> buildDoerSections(request)
            is AgentInstructionRequest.ReviewerRequest -> buildReviewerSections(request)
            is AgentInstructionRequest.PlannerRequest -> buildPlannerSections(request)
            is AgentInstructionRequest.PlanReviewerRequest -> buildPlanReviewerSections(request)
        }
        return writeInstructionsFile(request.outputDir, sections)
    }

    // -- Doer --

    private fun buildDoerSections(request: AgentInstructionRequest.DoerRequest): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. PrivateMd (silently skipped if absent)
        privateMdSection(request.outputDir)?.let { add(it) }
        // 3. Part context
        add(InstructionRenderers.partContext(
            request.executionContext.partName,
            request.executionContext.partDescription,
        ))
        // 4. Ticket
        add(ticketSection(request.ticketContent))
        // 5. PLAN.md (with-planning only)
        request.executionContext.planMdPath?.let { add(planSection(it)) }
        // 6. Prior PUBLIC.md files
        addAll(priorPublicMdSections(request.executionContext.priorPublicMdPaths))
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

    // -- Reviewer --

    private fun buildReviewerSections(request: AgentInstructionRequest.ReviewerRequest): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. PrivateMd (silently skipped if absent)
        privateMdSection(request.outputDir)?.let { add(it) }
        // 3. Part context
        add(InstructionRenderers.partContext(
            request.executionContext.partName,
            request.executionContext.partDescription,
        ))
        // 4. Ticket
        add(ticketSection(request.ticketContent))
        // 5. PLAN.md (with-planning only)
        request.executionContext.planMdPath?.let { add(planSection(it)) }
        // 6. Prior PUBLIC.md files
        addAll(priorPublicMdSections(request.executionContext.priorPublicMdPaths))
        // 7. Doer's PUBLIC.md for review
        add(doerOutputForReviewerSection(request.doerPublicMdPath))
        // 7a. Structured feedback format
        add(InstructionText.REVIEWER_FEEDBACK_FORMAT)
        // 7b-7d. Feedback state (iteration > 1)
        if (request.iterationNumber > 1) {
            addAll(feedbackStateSections(request.feedbackDir))
        }
        // 7e. Feedback writing instructions
        add(InstructionText.FEEDBACK_WRITING_INSTRUCTIONS)
        // 8. Output paths
        add(InstructionRenderers.publicMdOutputPath(request.publicMdOutputPath))
        // 9. PUBLIC.md writing guidelines
        add(InstructionText.PUBLIC_MD_WRITING_GUIDELINES)
        // 10. Callback script usage
        add(
            InstructionRenderers.callbackScriptUsage(
                forReviewer = true,
                includePlanValidation = false,
            )
        )
    }

    // -- Planner --

    private fun buildPlannerSections(request: AgentInstructionRequest.PlannerRequest): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. PrivateMd (silently skipped if absent)
        privateMdSection(request.outputDir)?.let { add(it) }
        // 3. Ticket
        add(ticketSection(request.ticketContent))
        // 4. Role catalog
        add(InstructionRenderers.roleCatalog(request.roleCatalogEntries))
        // 5. Available agent types & models
        add(InstructionText.AGENT_TYPES_AND_MODELS)
        // 6. Plan format instructions
        add(InstructionText.PLAN_FORMAT_INSTRUCTIONS)
        // 7. Reviewer feedback (iteration > 1)
        if (request.iterationNumber > 1 && request.planReviewerPublicMdPath != null) {
            add(reviewerFeedbackForDoerSection(request.planReviewerPublicMdPath))
        }
        // 8. plan_flow.json output path
        add(planJsonOutputPathSection(request.planJsonOutputPath))
        // 9. PLAN.md output path
        add(planMdOutputPathSection(request.planMdOutputPath))
        // 10. PUBLIC.md output path
        add(InstructionRenderers.publicMdOutputPath(request.publicMdOutputPath))
        // 11. PUBLIC.md writing guidelines
        add(InstructionText.PUBLIC_MD_WRITING_GUIDELINES)
        // 12. Callback script usage (includes validate-plan query)
        add(
            InstructionRenderers.callbackScriptUsage(
                forReviewer = false,
                includePlanValidation = true,
            )
        )
    }

    // -- Plan Reviewer --

    private fun buildPlanReviewerSections(
        request: AgentInstructionRequest.PlanReviewerRequest,
    ): List<String> = buildList {
        // 1. Role definition
        add(roleDefinitionSection(request.roleDefinition))
        // 2. PrivateMd (silently skipped if absent)
        privateMdSection(request.outputDir)?.let { add(it) }
        // 3. Ticket
        add(ticketSection(request.ticketContent))
        // 4. plan_flow.json content
        add(planJsonContentSection(request.planJsonContent))
        // 5. PLAN.md content
        add(planMdContentSection(request.planMdContent))
        // 6. Available agent types & models
        add(InstructionText.AGENT_TYPES_AND_MODELS)
        // 7. Planner's PUBLIC.md
        add(plannerPublicMdSection(request.plannerPublicMdPath))
        // 8. Iteration feedback (iteration > 1)
        if (request.iterationNumber > 1 && request.priorPlanReviewerPublicMdPath != null) {
            add(priorPlanReviewerFeedbackSection(request.priorPlanReviewerPublicMdPath))
        }
        // 9. PUBLIC.md output path
        add(InstructionRenderers.publicMdOutputPath(request.publicMdOutputPath))
        // 10. PUBLIC.md writing guidelines
        add(InstructionText.PUBLIC_MD_WRITING_GUIDELINES)
        // 11. Callback script usage (includes validate-plan query)
        add(
            InstructionRenderers.callbackScriptUsage(
                forReviewer = true,
                includePlanValidation = true,
            )
        )
    }

    // -- Per-section private methods --
    // Each reads a file or returns formatted text. Named to match the spec's section names.

    private fun roleDefinitionSection(role: RoleDefinition): String =
        "# Role: ${role.name}\n\n${role.filePath.readText()}"

    /**
     * Reads `${sub_part}/private/PRIVATE.md` if it exists.
     * outputDir = `${sub_part}/comm/in` -> parent.parent = `${sub_part}`
     * Returns null (silently skipped) when the file does not exist.
     */
    private fun privateMdSection(outputDir: Path): String? {
        val privateMdPath = outputDir.parent.parent.resolve("private/PRIVATE.md")
        return if (Files.exists(privateMdPath)) {
            "# Prior Session Context (PRIVATE.md)\n\n${privateMdPath.readText()}"
        } else {
            null
        }
    }

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

    // -- Planner-specific sections --

    private fun planJsonOutputPathSection(path: Path): String = """
        ## plan_flow.json Output Path

        Write your plan_flow.json to:
        `$path`
    """.trimIndent()

    private fun planMdOutputPathSection(path: Path): String = """
        ## PLAN.md Output Path

        Write your human-readable plan to:
        `$path`
    """.trimIndent()

    private fun planJsonContentSection(content: String): String =
        "# plan_flow.json\n\n```json\n$content\n```"

    private fun planMdContentSection(content: String): String =
        "# PLAN.md\n\n$content"

    private fun plannerPublicMdSection(path: Path): String =
        "# Planner's Rationale\n\n${path.readText()}"

    private fun priorPlanReviewerFeedbackSection(path: Path): String =
        "# Your Prior Feedback\n\n${path.readText()}"

    // -- Utility extension --

    private val AgentInstructionRequest.executionContextOrNull: ExecutionContext?
        get() = when (this) {
            is AgentInstructionRequest.DoerRequest -> executionContext
            is AgentInstructionRequest.ReviewerRequest -> executionContext
            is AgentInstructionRequest.PlannerRequest -> null
            is AgentInstructionRequest.PlanReviewerRequest -> null
        }

    // -- File writing --

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
