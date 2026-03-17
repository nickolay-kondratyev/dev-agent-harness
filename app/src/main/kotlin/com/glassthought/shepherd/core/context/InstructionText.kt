package com.glassthought.shepherd.core.context

/**
 * Static text constants used in agent instruction files.
 *
 * Each constant is a named multiline Markdown string. Static text references [ProtocolVocabulary]
 * constants via string templates, creating a compile-time link between vocabulary and instructions.
 *
 * **To tweak a section**: find it by name (Ctrl+F or IDE symbol search), edit the multiline
 * string, recompile. Keyword tests verify protocol keywords are still present.
 *
 * See ContextForAgentProvider spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for concatenation order.
 */
object InstructionText {

    // ── Section 10: PUBLIC.md writing guidelines ─────────────────────────────

    /**
     * Static guidance on what to write in PUBLIC.md.
     *
     * Spec: ContextForAgentProvider.md section 10.
     */
    val PUBLIC_MD_WRITING_GUIDELINES: String = """
        ## PUBLIC.md Writing Guidelines

        Your PUBLIC.md is your work log — the record of what you did, why, and what you learned.
        It is read by downstream agents and reviewers.

        Include:
        - **Decisions made** and rationale (especially non-obvious ones)
        - **What was done** — a summary of changes, not a diff
        - **Codebase discoveries** — key classes, patterns, gotchas, anchor points of interest
        - **Cross-cutting constraints** that affect other parts of the workflow
        - **Review verdicts** (if you are a reviewer): ${ProtocolVocabulary.DoneResult.PASS} or ${ProtocolVocabulary.DoneResult.NEEDS_ITERATION}

        Do NOT duplicate content already in the plan.
        Do NOT include code — reference file paths and line numbers instead.

        **Durable reasoning**: Capture important reasoning in the code itself (WHY-NOT comments),
        persistent documentation (CLAUDE.md, deep memory, .md notes), and the ticket — not just
        here. PUBLIC.md is read by downstream agents but does not persist beyond this workflow run.
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
          **in the code** using a ${ProtocolVocabulary.WHY_NOT} comment. This comment
          is NOT for the reviewer — it is for any future reader of this code who might have the
          same question.
        - **Do NOT push back for the sake of pushing back.** Only reject feedback when you
          genuinely believe the reviewer is incorrect or missing context.
        - **Document your reasoning in PUBLIC.md**: for each reviewer point, state whether you
          accepted or rejected it and why.
    """.trimIndent()

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
