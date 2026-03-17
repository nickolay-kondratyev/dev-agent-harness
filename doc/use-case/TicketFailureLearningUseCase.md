# TicketFailureLearningUseCase / ap.cI3odkAZACqDst82HtxKa.E

When a `shepherd run` fails (try-N), the next try starts fresh with zero knowledge of what
was attempted. The system is amnesiac across tries — agents repeat the same failed approaches.

**Solution**: Run a non-interactive ClaudeCode agent (`--print`, sonnet) that reads `.ai_out/`
artifacts, generates a structured failure summary, and appends a `## Previous Failed Attempts`
section to the ticket. The ticket already feeds into all agents
(`ContextForAgentProvider` ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). Non-shepherd consumers
(humans, other tools) also benefit.

---

## Design

Delegates the **entire job** to `NonInteractiveAgentRunner`
(ref.ap.ad4vG4G2xMPiMHRreoYVr.E) with ClaudeCode in `--print` mode. The agent can:

- **Read files directly** — browses `.ai_out/` artifacts for richer analysis than
  prompt-stuffed LLM calls
- **Generate structured summary** — approach, root cause, recommendations
- **Update the ticket** — append `### TRY-{N}` section
- **Handle git operations** — commit on try branch, attempt best-effort propagation to
  originating branch

This replaces the previous design of `DirectBudgetHighLLM` + harness-coded git operations.
The agent handles the complexity naturally — including error recovery if git operations fail.

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

1. **Assemble agent instructions** — build a focused instruction string from
   `FailureLearningRequest` fields:
   - Structured failure facts (try number, branch, failure type, where it failed, iteration)
   - Path to `.ai_out/` directory — agent reads artifacts directly
   - Path to ticket file — agent appends the `### TRY-{N}` section
   - Originating branch name — for best-effort propagation
   - Expected output format (see [TRY-N Format](#try-n-format))
   - Git commit instructions (commit on try branch, attempt propagation)

2. **Run agent** — invoke `NonInteractiveAgentRunner.run()`
   (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) with:
   - **Agent type**: `ClaudeCode` — needs file reading capability for artifact analysis
   - **Model**: `sonnet` — cost-effective but capable enough for analysis + git operations
   - **Timeout**: 10 minutes — agent reads files, generates summary, performs git operations

3. **Interpret result** — **non-fatal** regardless of outcome:
   - `Success` → log INFO, continue
   - `Failed` → log WARN with agent output, continue
   - `TimedOut` → log WARN, continue
   - The use case **never** fails the workflow — learning is best-effort

---

## Agent Instructions — What the Agent Does

The agent receives instructions to:

1. **Read agent artifacts** from `.ai_out/`:
   - `current_state.json` — workflow progress, session records
   - All `PUBLIC.md` files across sub-parts (agent outputs)
   - `PLAN.md` (from `shared/plan/`) if present

2. **Generate a structured summary**:
   - **Approach**: what was attempted
   - **Root Cause**: why it failed
   - **Recommendations**: what the next try should do differently

3. **Append to ticket**: If `## Previous Failed Attempts` heading does not exist, add it.
   Append the `### TRY-{N}` subsection under that heading.

4. **Commit on try branch**:
   ```
   git add {ticketPath} && git commit -m "[shepherd] ticket-failure-learning — TRY-{N}"
   ```

5. **Best-effort propagation to originating branch**: The agent attempts to propagate the
   ticket update to the originating branch so the next try inherits it. If any git operation
   fails, the agent skips propagation — the learning is preserved on the try branch.
   ```
   git checkout {originatingBranch}
   git checkout {tryBranch} -- {ticketPath}
   git commit -m "[shepherd] ticket-failure-learning — TRY-{N} (propagated)"
   git checkout {tryBranch}
   ```

### TRY-N Format

```markdown
### TRY-{N}

- **Branch**: `{branchName}`
- **Workflow**: {workflowType}
- **Failure type**: {failureType}
- **Failed at**: {failedAt} (iteration {iteration})
- **Parts completed**: {partsCompleted, comma-separated}

#### Summary

**Approach**: {agent-generated from artifact analysis}
**Root Cause**: {agent-generated from artifact analysis}
**Recommendations**: {agent-generated from artifact analysis}
```

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| **Agent fails entirely** (crash, non-zero exit) | Log WARN. Learning is lost for this try — structured facts are not recorded. Non-fatal. |
| **Agent times out** | Log WARN. Kill process. Same as agent failure. |
| **No `.ai_out/` directory** | Agent handles gracefully — generates summary from structured facts only. |
| **No `PUBLIC.md` files found** | Agent proceeds — less context but can still summarize from `current_state.json` and structured facts. |
| **Ticket already has `## Previous Failed Attempts` section** | Agent appends new `### TRY-{N}` subsection under the existing heading. |
| **Git propagation fails** | Agent logs the error and returns — learning is preserved on the try branch. |
| **Originating branch deleted** | Agent skips propagation — learning is preserved on the try branch. |

---

## Dependencies

- `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) — run ClaudeCode agent as subprocess

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
- Does **not** call `DirectBudgetHighLLM` — the ClaudeCode agent handles both file reading
  and summary generation.
