package com.glassthought.shepherd.core.context

import java.nio.file.Path

/**
 * Rendering functions that produce formatted instruction strings from data inputs.
 *
 * Each function takes explicit parameters and returns a formatted Markdown string.
 * These are the dynamic counterparts to the static constants in [InstructionText].
 *
 * See ContextForAgentProvider spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for concatenation order.
 */
object InstructionRenderers {

    // ── Section 2: Part context ──────────────────────────────────────────────

    /**
     * Renders the part context section — tells the agent which part of the workflow it executes.
     */
    fun partContext(partName: String, partDescription: String): String = """
        ## Part Context

        **Part:** $partName
        **Description:** $partDescription

        You are executing one part of a larger workflow. The ticket below describes the full task,
        but your responsibility is limited to this part.
    """.trimIndent()

    // ── Section 9: Output path ───────────────────────────────────────────────

    /**
     * Renders the PUBLIC.md output path section.
     */
    fun publicMdOutputPath(path: Path): String = """
        ## Output Path

        Write your PUBLIC.md to:
        `$path`
    """.trimIndent()

    // ── Section 12: Callback script usage ────────────────────────────────────

    /**
     * Callback script usage block — wrapped in compaction-survival tags.
     *
     * This is role-parameterized: [forReviewer] controls which done result values are shown.
     * [includePlanValidation] adds the `validate-plan` query for planning-phase agents.
     *
     * Spec: ContextForAgentProvider.md — Callback Script Help section.
     */
    fun callbackScriptUsage(forReviewer: Boolean, includePlanValidation: Boolean): String {
        val doneExamples = if (forReviewer) {
            """
            |When you complete your review:
            |`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.DONE} ${ProtocolVocabulary.DoneResult.PASS}`             (if work passes)
            |`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.DONE} ${ProtocolVocabulary.DoneResult.NEEDS_ITERATION}`  (if work needs changes)
            """.trimMargin()
        } else {
            """
            |When you complete your task:
            |`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.DONE} ${ProtocolVocabulary.DoneResult.COMPLETED}`
            """.trimMargin()
        }

        val querySection = if (includePlanValidation) {
            """
            |
            |### Queries (read the response from stdout):
            |
            |Validate plan before signaling done:
            |`${ProtocolVocabulary.CALLBACK_QUERY_SCRIPT} validate-plan /absolute/path/to/plan.json`
            """.trimMargin()
        } else {
            ""
        }

        return """
            |<${ProtocolVocabulary.PAYLOAD_ACK_TAG}>
            |## Communicating with the Harness
            |
            |Two scripts on your ${'$'}PATH — one for fire-and-forget signals, one for queries that return data.
            |
            |### Signals (fire-and-forget — ignore stdout):
            |
            |**Payload ACK — MUST do first when you receive a wrapped payload:**
            |When you receive input wrapped in `<${ProtocolVocabulary.PAYLOAD_ACK_TAG}>` XML tags, you MUST
            |call the command in the `MUST_ACK_BEFORE_PROCEEDING` attribute BEFORE processing the
            |payload content:
            |`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.ACK_PAYLOAD} <payload_id>`
            |The `payload_id` and exact command are in the XML wrapper — copy it exactly.
            |
            |$doneExamples
            |
            |If you have a question for the human:
            |`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.USER_QUESTION} "Your question here"`
            |Wait for the answer — it will arrive via your input.
            |
            |If you hit an unrecoverable error:
            |`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.FAIL_WORKFLOW} "Reason for failure"`
            |
            |Health ping acknowledgment (when asked):
            |`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.PING_ACK}`
            |$querySection
            |</${ProtocolVocabulary.PAYLOAD_ACK_TAG}>
        """.trimMargin()
    }

    // ── Section 8a: Per-feedback-item instructions (doer, inner loop) ────────

    /**
     * Instruction text for a doer processing a single feedback item in the inner feedback loop.
     *
     * Spec: granular-feedback-loop.md (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) — Doer Instructions
     * Per Feedback Item section.
     */
    fun feedbackItemInstructions(
        feedbackContent: String,
        currentPath: Path,
        addressedPath: Path,
        rejectedPath: Path,
        isOptional: Boolean,
    ): String {
        val optionalNote = if (isOptional) {
            """
            |
            |**This feedback is ${ProtocolVocabulary.Severity.OPTIONAL}.** You may choose to skip it.
            |If skipping, simply signal done without moving the file. The feedback will remain in
            |${ProtocolVocabulary.FeedbackStatus.UNADDRESSED}/ but will NOT block part completion.
            """.trimMargin()
        } else {
            ""
        }

        return """
            |## Feedback Item to Address
            |
            |$feedbackContent
            |
            |## Instructions
            |
            |1. Address the feedback item above in the codebase.
            |2. When done, move the feedback file from its current location to the corresponding
            |   `${ProtocolVocabulary.FeedbackStatus.ADDRESSED}/` directory. Add a movement record
            |   (see ${ProtocolVocabulary.MOVEMENT_LOG} format below).
            |3. If you disagree with this feedback, move it to `${ProtocolVocabulary.FeedbackStatus.REJECTED}/`
            |   instead, with a ${ProtocolVocabulary.WHY_NOT} justification in the movement record.
            |4. Update your PUBLIC.md with a brief one-liner noting this item was
            |   ${ProtocolVocabulary.FeedbackStatus.ADDRESSED}/${ProtocolVocabulary.FeedbackStatus.REJECTED}.
            |5. Signal done: `${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.DONE} ${ProtocolVocabulary.DoneResult.COMPLETED}`
            |$optionalNote
            |
            |### ${ProtocolVocabulary.MOVEMENT_LOG} Format
            |
            |Append to the `## ${ProtocolVocabulary.MOVEMENT_LOG}` section of the feedback file:
            |
            |```markdown
            |### [YYYY-MM-DDTHH:MM:SSZ] Moved by: <role> | From: <source_dir> → To: <target_dir>
            |<brief justification — WHY this was ${ProtocolVocabulary.FeedbackStatus.ADDRESSED}/${ProtocolVocabulary.FeedbackStatus.REJECTED}>
            |```
            |
            |### Current feedback file path
            |`$currentPath`
            |
            |### Target paths
            |- ${ProtocolVocabulary.FeedbackStatus.ADDRESSED}: `$addressedPath`
            |- ${ProtocolVocabulary.FeedbackStatus.REJECTED}: `$rejectedPath`
        """.trimMargin()
    }

    // ── Planner role catalog ─────────────────────────────────────────────────

    /**
     * Renders role catalog section for the planner.
     */
    fun roleCatalog(
        roles: List<RoleCatalogEntry>,
    ): String = buildString {
        appendLine("## Available Roles")
        appendLine()
        roles.forEach { role ->
            appendLine("### ${role.name}")
            appendLine(role.description)
            role.descriptionLong?.let {
                appendLine()
                appendLine(it)
            }
            appendLine()
        }
    }

    /**
     * Minimal role info for the planner's role catalog section.
     * Avoids coupling to [com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition].
     */
    data class RoleCatalogEntry(
        val name: String,
        val description: String,
        val descriptionLong: String?,
    )
}
