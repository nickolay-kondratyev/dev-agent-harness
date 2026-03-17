# TicketFailureLearningUseCase / ap.cI3odkAZACqDst82HtxKa.E

When a `shepherd run` fails (try-N), the next try starts fresh with zero knowledge of what
was attempted. The system is amnesiac across tries — agents repeat the same failed approaches.

**Solution**: Run a non-interactive ClaudeCode agent (`--print`, sonnet) that reads `.ai_out/`
artifacts and **produces a structured failure summary on stdout**. The **harness** then appends
a `## Previous Failed Attempts` section to the ticket and handles all git operations (commit,
propagation). The ticket already feeds into all agents (`ContextForAgentProvider`
ref.ap.9HksYVzl1KkR9E1L2x8Tx.E). Non-shepherd consumers (humans, other tools) also benefit.

---

## Design

**Agent responsibility**: Read artifacts, produce text. The agent runs via
`NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) with ClaudeCode in `--print`
mode. It can:

- **Read files directly** — browses `.ai_out/` artifacts for richer analysis than
  prompt-stuffed LLM calls
- **Generate structured summary** — approach, root cause, recommendations
- **Output the result on stdout** — the harness captures this output

**Harness responsibility**: All mutations (ticket file update, git operations). The harness:

- Appends the `### TRY-{N}` section to the ticket file
- Commits on the try branch using existing git infrastructure
- Attempts best-effort propagation to the originating branch

**Why**: Git operations performed by automated agents are inherently fragile (dirty working
tree, branch divergence, race conditions). The harness already owns all other git operations
via `GitCommitStrategy`. Having the agent also do git creates two separate git operation paths.
By keeping the agent text-only, it has one job (analyze), and the harness has one job
(git operations) — no overlap, no fragility.

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
   - Expected output format (see [Agent Output Format](#agent-output-format))
   - **No git instructions** — agent only produces text

2. **Run agent** — invoke `NonInteractiveAgentRunner.run()`
   (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) with:
   - **Agent type**: `ClaudeCode` — needs file reading capability for artifact analysis
   - **Model**: `sonnet` — cost-effective but capable enough for analysis
   - **Timeout**: 10 minutes — agent reads files and generates summary

3. **Process agent output** — on `Success`:
   a. Build the `### TRY-{N}` section by combining structured facts from
      `FailureLearningRequest` with the agent-generated summary (see [TRY-N Format](#try-n-format))
   b. Append to ticket file: if `## Previous Failed Attempts` heading does not exist, add it.
      Append the `### TRY-{N}` subsection under that heading.
   c. **Commit on try branch**:
      `git add {ticketPath} && git commit -m "[shepherd] ticket-failure-learning — TRY-{N}"`
   d. **Best-effort propagation to originating branch** (see [Propagation](#best-effort-propagation))

4. **Handle agent failure** — on `Failed` or `TimedOut`:
   a. Build a minimal `### TRY-{N}` section from structured facts only (no agent summary)
   b. Append to ticket, commit, and propagate — same as step 3b–3d
   c. Log WARN with agent output/timeout info

5. **Non-fatal** — the use case **never** fails the workflow. Learning is best-effort.
   Even if the commit or propagation fails, log WARN and continue.

### Best-Effort Propagation

The harness propagates the ticket update to the originating branch so the next try inherits it.
If **any** git operation fails, skip propagation — the learning is preserved on the try branch.

```bash
git checkout {originatingBranch}
git checkout {tryBranch} -- {ticketPath}
git commit -m "[shepherd] ticket-failure-learning — TRY-{N} (propagated)"
git checkout {tryBranch}
```

---

## Agent Instructions — What the Agent Does

The agent receives instructions to:

1. **Read agent artifacts** from `.ai_out/`:
   - `current_state.json` — workflow progress, session records
   - All `PUBLIC.md` files across sub-parts (agent outputs)
   - `PLAN.md` (from `shared/plan/`) if present

2. **Output a structured summary to stdout** containing:
   - **Approach**: what was attempted
   - **Root Cause**: why it failed
   - **Recommendations**: what the next try should do differently

The agent does **not** modify any files or perform any git operations. It is a pure
read-and-analyze task.

### Agent Output Format

The agent outputs plain text to stdout in this format:

```
**Approach**: {agent-generated from artifact analysis}
**Root Cause**: {agent-generated from artifact analysis}
**Recommendations**: {agent-generated from artifact analysis}
```

The harness combines this with structured facts to build the full `### TRY-{N}` section.

### TRY-N Format

The harness constructs this from `FailureLearningRequest` fields + agent output:

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

If the agent failed or timed out, the `#### Summary` section is replaced with:

```markdown
#### Summary

*Agent failed to produce summary — structured facts above are the only record for this try.*
```

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| **Agent fails entirely** (crash, non-zero exit) | Log WARN. Harness still records structured facts (without agent summary) to the ticket. Non-fatal. |
| **Agent times out** | Log WARN. Kill process. Harness still records structured facts. Non-fatal. |
| **No `.ai_out/` directory** | Agent handles gracefully — generates summary from structured facts in its instructions only. |
| **No `PUBLIC.md` files found** | Agent proceeds — less context but can still summarize from `current_state.json` and structured facts. |
| **Ticket already has `## Previous Failed Attempts` section** | Harness appends new `### TRY-{N}` subsection under the existing heading. |
| **Git commit fails on try branch** | Log WARN, skip propagation, continue. Non-fatal. |
| **Propagation fails** (originating branch diverged, deleted, dirty) | Log WARN, skip. Learning is preserved on the try branch. |

---

## Dependencies

- `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) — run ClaudeCode agent as subprocess

---

## What It Does NOT Do

- Does **not** let the agent modify any files — agent is read-only + stdout output.
- Does **not** let the agent perform git operations — all git is harness-owned.
- Does **not** modify `ContextForAgentProvider` — the ticket is already part of agent context.
  Updating the ticket is sufficient to propagate learning to all future agents.
- Does **not** retry the failed workflow — it only records what happened for the next try.
- Does **not** push any branches — pushing is the caller's responsibility.
- Does **not** halt or exit — it returns control to the caller (`FailedToExecutePlanUseCase`),
  which handles the halt/exit flow.
- Does **not** clean up `.ai_out/` or TMUX sessions — those are handled by
  `FailedToExecutePlanUseCase` before and after this use case runs.
