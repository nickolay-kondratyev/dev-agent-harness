# TicketFailureLearningUseCase / ap.cI3odkAZACqDst82HtxKa.E

When a `shepherd run` fails (try-N), the next try starts fresh with zero knowledge of what
was attempted. The system is amnesiac across tries — agents repeat the same failed approaches.

**Solution**: Update the ticket itself with a `## Previous Failed Attempts` section containing
structured failure context + LLM summary per try. The ticket already feeds into all agents
(Section 3 for execution agents, Section 2 for planner/plan reviewer in `ContextForAgentProvider`
ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). No changes to `ContextForAgentProvider` needed. Non-shepherd
consumers (humans, other tools) also benefit.

---

## Interface

```kotlin
interface TicketFailureLearningUseCase {
    suspend fun recordFailureLearning(request: FailureLearningRequest): Unit
}

data class FailureLearningRequest(
    /** Path to the ticket markdown file */
    val ticketPath: String,

    /** Current try number (e.g., 1 for try-1) */
    val tryNumber: Int,

    /** The try branch name (e.g., nid_abc__dashboard__try-1) */
    val branchName: String,

    /** The branch from which the try branch was created */
    val originatingBranch: String,

    /** The sealed PartResult that caused the failure */
    val failureContext: PartResultFailureContext,

    /** Path to the .ai_out/ directory for this try */
    val aiOutDir: String,
)

data class PartResultFailureContext(
    /** Workflow type (e.g., "with-planning", "straightforward") */
    val workflowType: String,

    /** Failure type (e.g., "FailedWorkflow", "AgentCrashed", "FailedToConverge") */
    val failureType: String,

    /** Which part failed (e.g., "backend_impl/impl") */
    val failedAt: String,

    /** Current iteration at time of failure (e.g., "1/3") */
    val iteration: String,

    /** Parts that completed successfully before the failure */
    val partsCompleted: List<String>,
)
```

---

## Flow

1. **Read agent artifacts** from `.ai_out/`:
   - `current_state.json` — workflow progress, session records
   - All `PUBLIC.md` files across sub-parts (agent outputs)
   - `SHARED_CONTEXT.md` if present
   - `PLAN.md` (from `harness_private/`) if present

2. **Call `DirectBudgetHighLLM`** (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) to generate a structured
   summary. Prompt includes:
   - The failure context (type, where it failed, iteration)
   - All collected agent artifacts
   - Instruction to produce: **Approach** (what was attempted), **Root Cause** (why it failed),
     **Recommendations** (what the next try should do differently)

3. **Build structured TRY-{N} markdown block** combining facts from `FailureLearningRequest`
   with the LLM summary:
   ```markdown
   ### TRY-{N}

   - **Branch**: `{branchName}`
   - **Workflow**: {workflowType}
   - **Failure type**: {failureType}
   - **Failed at**: {failedAt} (iteration {iteration})
   - **Parts completed**: {partsCompleted, comma-separated}

   #### Summary

   **Approach**: {LLM-generated}
   **Root Cause**: {LLM-generated}
   **Recommendations**: {LLM-generated}
   ```

4. **Append to ticket**: Read the ticket file. If `## Previous Failed Attempts` heading does
   not exist, append it. Append the `### TRY-{N}` subsection under that heading.

5. **Commit on try branch**:
   ```
   git add {ticketPath} && git commit -m "[shepherd] ticket-failure-learning — TRY-{N}"
   ```
   Uses the standard harness commit author (same as other `[shepherd]` commits).

6. **Propagate to originating branch**: The learning must be available on the originating
   branch so the next try (which branches from the originating branch) inherits it.
   - `git checkout {originatingBranch}`
   - `git checkout {tryBranch} -- {ticketPath}` — extract just the ticket file from the try branch
   - `git commit -m "[shepherd] ticket-failure-learning — TRY-{N} (propagated)"`
   - `git checkout {tryBranch}` — return to the try branch

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| **LLM failure** (timeout, API error) | Log WARN. Use static fallback text: `"LLM summary unavailable — review agent artifacts on branch {branchName} in .ai_out/"`. The structured facts (branch, failure type, etc.) are still recorded. |
| **Originating branch deleted** | Log WARN, skip propagation. Learning is preserved on the try branch — the human can cherry-pick if needed. |
| **Git propagation failure** (checkout fails, commit fails) | Log ERROR, skip propagation. Learning is preserved on the try branch. The use case does NOT delegate to `FailedToExecutePlanUseCase` — propagation failure is non-fatal. |
| **Ticket already has `## Previous Failed Attempts` section** | Append new `### TRY-{N}` subsection under the existing heading. Do NOT create a duplicate heading. |
| **No `.ai_out/` directory** | Log WARN, proceed with empty artifacts. The structured facts are still valuable without the LLM summary. |
| **No `PUBLIC.md` files found** | Proceed normally — the LLM gets less context but can still summarize based on `current_state.json` and failure context. |

---

## Dependencies

- `DirectBudgetHighLLM` (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) — LLM summarization of failure context
- `FailedToExecutePlanUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) — the caller that invokes this use case before halting

---

## What It Does NOT Do

- Does **not** modify `ContextForAgentProvider` — the ticket is already part of agent context.
  Updating the ticket is sufficient to propagate learning to all future agents.
- Does **not** retry the failed workflow — it only records what happened for the next try.
- Does **not** push any branches — pushing is the caller's responsibility.
- Does **not** halt or exit — it returns control to the caller (`FailedToExecutePlanUseCase`),
  which handles the halt/exit flow.
- Does **not** clean up `.ai_out/` or TMUX sessions — those are handled by
  `FailedToExecutePlanUseCase` before and after this use case runs.
