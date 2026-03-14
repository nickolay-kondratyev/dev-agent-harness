package com.glassthought.shepherd.core.context

import java.nio.file.Path

/**
 * All static text sections that are concatenated into agent instruction files.
 *
 * Each section is a named `val` or function — easy to find via symbol search, easy to edit,
 * easy to verify in keyword tests. Static text references [ProtocolVocabulary] constants
 * via string templates, creating a compile-time link between vocabulary and instructions.
 *
 * **To tweak a section**: find it by name (Ctrl+F or IDE symbol search), edit the multiline
 * string, recompile. Keyword tests verify protocol keywords are still present.
 *
 * See ContextForAgentProvider spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for concatenation order.
 */
object InstructionSections {

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

    /**
     * Renders the SHARED_CONTEXT.md path section.
     */
    fun sharedContextMdPath(path: Path): String = """
        ## SHARED_CONTEXT.md Path

        You may read and update the shared context file at:
        `$path`
    """.trimIndent()

    // ── Section 10: PUBLIC.md writing guidelines ─────────────────────────────

    /**
     * Static guidance on what to write in PUBLIC.md.
     *
     * Spec: ContextForAgentProvider.md section 10.
     */
    val PUBLIC_MD_WRITING_GUIDELINES: String = """
        ## PUBLIC.md Writing Guidelines

        Your PUBLIC.md is your work log — the record of what you did, why, and what you decided.
        It is read by downstream agents and reviewers.

        Include:
        - **Decisions made** and rationale (especially non-obvious ones)
        - **What was done** — a summary of changes, not a diff
        - **Review verdicts** (if you are a reviewer): ${ProtocolVocabulary.DoneResult.PASS} or ${ProtocolVocabulary.DoneResult.NEEDS_ITERATION}

        Do NOT duplicate content from the plan or SHARED_CONTEXT.md.
        Do NOT include code — reference file paths and line numbers instead.
    """.trimIndent()

    // ── Section 11: SHARED_CONTEXT.md writing guidelines ─────────────────────

    /**
     * Static guidance on what to write in SHARED_CONTEXT.md.
     *
     * Spec: ContextForAgentProvider.md section 11. See also ai-out-directory.md
     * (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).
     */
    val SHARED_CONTEXT_MD_GUIDELINES: String = """
        ## SHARED_CONTEXT.md Writing Guidelines

        SHARED_CONTEXT.md is a shared knowledge base across all agents in this workflow.
        Update it in place — do NOT append duplicates.

        Good content:
        - Codebase discoveries (key classes, patterns, gotchas)
        - Anchor points of interest (ap.XXX references)
        - Cross-cutting constraints that affect multiple parts
        - Patterns observed that downstream agents should follow

        Bad content:
        - Your specific decisions (those belong in PUBLIC.md)
        - Temporary state or progress notes
        - Content already in the plan
    """.trimIndent()

    // ── Section 7: Reviewer iteration context ────────────────────────────────

    /**
     * Static structured feedback format guidance for reviewers.
     *
     * Spec: Structured Reviewer Feedback Contract (ref.ap.EslyJMFQq8BBrFXCzYw5P.E).
     */
    val REVIEWER_FEEDBACK_FORMAT: String = """
        ## Structured Feedback Format

        When your verdict is `${ProtocolVocabulary.DoneResult.NEEDS_ITERATION}`, your PUBLIC.md
        MUST follow this structure:

        ```markdown
        ## Verdict: ${ProtocolVocabulary.DoneResult.NEEDS_ITERATION}

        ## Issues
        - [ ] <issue-1>: <description> | Severity: must-fix | File(s): <path>
        - [ ] <issue-2>: <description> | Severity: should-fix | File(s): <path>

        ## ${ProtocolVocabulary.WHY_NOT} Pitfalls to Document
        <approaches the doer tried that should get ${ProtocolVocabulary.WHY_NOT} comments>

        ## Acceptance Criteria for Next Iteration
        <concrete checklist the doer must satisfy>

        ## What Passed (do NOT regress)
        <things that are good and must be preserved>
        ```

        On `${ProtocolVocabulary.DoneResult.PASS}`, a brief summary is sufficient.
    """.trimIndent()

    // ── Section 7d: Feedback writing instructions (for reviewers) ────────────

    /**
     * Instructions for reviewers on writing individual feedback files to `__feedback/`.
     *
     * Spec: granular-feedback-loop.md (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E), R2.
     */
    val FEEDBACK_WRITING_INSTRUCTIONS: String = """
        ## Writing Feedback Files

        In addition to PUBLIC.md, write **individual feedback files** — one per actionable issue.

        ### Where to write
        Write each file to the appropriate severity directory:
        - `__feedback/${ProtocolVocabulary.FeedbackStatus.UNADDRESSED}/${ProtocolVocabulary.Severity.CRITICAL}/` — must be fixed, blocks completion
        - `__feedback/${ProtocolVocabulary.FeedbackStatus.UNADDRESSED}/${ProtocolVocabulary.Severity.IMPORTANT}/` — must be fixed, blocks completion
        - `__feedback/${ProtocolVocabulary.FeedbackStatus.UNADDRESSED}/${ProtocolVocabulary.Severity.OPTIONAL}/` — nice-to-have, does not block

        ### File format
        Name each file with a descriptive slug (e.g., `missing-null-check-in-parser.md`).

        ```markdown
        # <issue title>

        **File(s):** `<path/to/affected/file.kt>`

        <Detailed description of the issue and what needs to change>

        ---

        ## ${ProtocolVocabulary.MOVEMENT_LOG}

        <!-- Appended by whoever moves this file between directories -->
        ```
    """.trimIndent()

    // ── Section 8: Doer iteration feedback ───────────────────────────────────

    /**
     * Pushback guidance for doers on iteration > 1.
     *
     * Spec: ContextForAgentProvider.md — Doer Pushback Guidance section.
     */
    val DOER_PUSHBACK_GUIDANCE: String = """
        ## Handling Reviewer Feedback

        You have received feedback from the reviewer. Address each point:

        - **If you agree**: implement the requested changes.
        - **If you disagree**: you are empowered to push back, but you MUST defend your decision
          **in the code** using a ${ProtocolVocabulary.WHY_NOT} comment (see below). This comment
          is NOT for the reviewer — it is for any future reader of this code who might have the
          same question.
        - **Do NOT push back for the sake of pushing back.** Only reject feedback when you
          genuinely believe the reviewer is incorrect or missing context.
        - **Document your reasoning in PUBLIC.md**: for each reviewer point, state whether you
          accepted or rejected it and why.
    """.trimIndent()

    // ── Section 8b: WHY-NOT reminder ─────────────────────────────────────────

    /**
     * Brief reminder to place WHY-NOT comments. Included for all doer iterations.
     *
     * Spec: WHY-NOT Comments Protocol (ref.ap.kmiKk7vECiNSpJjAXYMyE.E).
     */
    val WHY_NOT_REMINDER: String = """
        ## ${ProtocolVocabulary.WHY_NOT} Comments — Durable Pitfall Documentation

        When you reject a reviewer suggestion, discover a dead-end approach, or fix a rejected
        approach, place a `${ProtocolVocabulary.WHY_NOT}` comment at the code location where
        someone might naturally attempt the wrong approach:

        ```
        // ${ProtocolVocabulary.WHY_NOT}(YYYY-MM-DD): Don't use <approach> here — <concise reason>.
        // <what constraint makes it wrong>. Revisit if <conditions change>.
        ```

        ${ProtocolVocabulary.WHY_NOT} comments are NOT carved in stone — they represent the best
        understanding at the time. Future agents may override if circumstances change.
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

    // ── Planner-specific sections ────────────────────────────────────────────

    /**
     * Available agent types and models — used by planner and plan reviewer.
     *
     * Spec: Agent type & model assignment (ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E).
     */
    val AGENT_TYPES_AND_MODELS: String = """
        ## Available Agent Types & Models

        V1 supports one agent type:

        | agentType | Description | Models |
        |-----------|-------------|--------|
        | `ClaudeCode` | Claude Code CLI agent in TMUX session | `opus` (high), `sonnet` (budget-high) |

        Each sub-part in the plan must specify an `agentType` and `model`.
    """.trimIndent()

    /**
     * Plan format instructions — JSON schema guidance for planner.
     *
     * Spec: plan-and-current-state.md (ref.ap.56azZbk7lAMll0D4Ot2G0.E).
     */
    val PLAN_FORMAT_INSTRUCTIONS: String = """
        ## Plan Format

        Write `plan.json` following this schema. Each part has sub-parts (typically doer + reviewer).

        ```json
        {
          "parts": [
            {
              "name": "<part_name>",
              "description": "<what this part accomplishes>",
              "sub_parts": [
                {
                  "name": "<sub_part_name>",
                  "role": "<role from catalog>",
                  "agentType": "ClaudeCode",
                  "model": "opus|sonnet",
                  "loadsPlan": false
                }
              ],
              "iteration": { "max": 3 }
            }
          ]
        }
        ```

        At least one implementor sub-part must have `loadsPlan: true` to receive PLAN.md.
    """.trimIndent()

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

    // ── Reviewer iteration > 1: feedback state sections header ───────────────

    /**
     * Header text for the addressed feedback section shown to reviewers on iteration > 1.
     */
    val ADDRESSED_FEEDBACK_HEADER: String = """
        ## Addressed Feedback (${ProtocolVocabulary.FeedbackStatus.ADDRESSED})

        The doer ${ProtocolVocabulary.FeedbackStatus.ADDRESSED} the following items.
        Verify that the fixes are correct.
    """.trimIndent()

    /**
     * Header text for the rejected feedback section shown to reviewers on iteration > 1.
     */
    val REJECTED_FEEDBACK_HEADER: String = """
        ## Rejected Feedback (${ProtocolVocabulary.FeedbackStatus.REJECTED})

        The doer ${ProtocolVocabulary.FeedbackStatus.REJECTED} the following items.
        Review the ${ProtocolVocabulary.WHY_NOT} reasoning. If you disagree, move the file
        back to `${ProtocolVocabulary.FeedbackStatus.UNADDRESSED}/` with a movement record.
    """.trimIndent()

    /**
     * Header text for skipped optional feedback shown to reviewers on iteration > 1.
     */
    val SKIPPED_OPTIONAL_HEADER: String = """
        ## Skipped ${ProtocolVocabulary.Severity.OPTIONAL} Feedback

        These ${ProtocolVocabulary.Severity.OPTIONAL} items remain in
        `${ProtocolVocabulary.FeedbackStatus.UNADDRESSED}/${ProtocolVocabulary.Severity.OPTIONAL}/`.
        You may accept or escalate to `${ProtocolVocabulary.Severity.IMPORTANT}` by moving them.
    """.trimIndent()
}
