# AutoRecoveryByAgentUseCase / ap.q54vAxzZnmWHuumhIQQWt.E

> **⚠️ V2 — DEFERRED FROM V1**
>
> This use case is deferred to V2. In V1, git operation failures use a simpler approach:
> **index.lock fast-path** (deterministic delete + retry) for the most common failure,
> and **immediate fail-fast** to `FailedToExecutePlanUseCase` for all other git failures.
>
> **Why deferred:**
> - Fail-fast is MORE robust than hoping a recovery agent can fix infrastructure issues
> - Recovery agents have their own failure modes (agent crash, timeout, wrong fix)
> - 20-min timeout means 20 minutes of waiting before the user even knows something is wrong
> - Index.lock fast-path handles ~80% of git failures (Pareto)
> - Clear error message lets human diagnose and fix the actual issue
>
> See [GitOperationFailureUseCase (V1)](../core/git.md#git-operation-failure-handling)
> (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E) for the simplified V1 approach.

---

## V2 Design (preserved for future implementation)

A general-purpose recovery mechanism that runs a lightweight non-interactive agent to attempt
automated recovery from infrastructure failures that occur mid-workflow.

### Motivation

Certain infrastructure failures (e.g., git commit failures due to disk full, `.gitignore`
conflicts, index lock files) are recoverable — a skilled agent can diagnose the issue,
apply a fix, and allow the workflow to continue. Rather than halting immediately and waiting
for human intervention, the harness first attempts automated recovery via a dedicated agent.

### Design

`AutoRecoveryByAgentUseCase` is a **generic recovery executor**. It does not know about git
or any specific failure domain — it receives structured context describing what failed and
what needs to happen, runs a recovery agent, and reports success or failure.

Domain-specific use cases (e.g., `GitOperationFailureUseCase` ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E)
package the failure context and delegate to `AutoRecoveryByAgentUseCase`.

**Uses `NonInteractiveAgentRunner`** (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) — a subprocess-based
agent invocation with no TMUX, no handshake, no health monitoring. Infrastructure recovery
tasks are short-lived and don't need the full interactive session machinery.

### Interface

```kotlin
interface AutoRecoveryByAgentUseCase {
    suspend fun attemptRecovery(request: RecoveryRequest): RecoveryOutcome
}

data class RecoveryRequest(
    /** Human-readable description of what failed */
    val failureDescription: String,

    /** The error output (stderr, stack trace, etc.) */
    val errorOutput: String,

    /** Structured context about the current workflow state */
    val workflowContext: String,

    /** What the recovery agent should achieve */
    val recoveryGoal: String,

    /** What the agent should NOT do (guardrails) */
    val constraints: List<String>,
)

sealed class RecoveryOutcome {
    /** Recovery succeeded — caller should retry the original operation once */
    object Resolved : RecoveryOutcome()

    /**
     * Recovery did not succeed. Either the recovery agent failed/timed out,
     * or the agent explicitly signalled that the issue requires human intervention.
     * Caller should delegate to FailedToExecutePlanUseCase with the given [reason].
     */
    data class Unresolvable(val reason: String) : RecoveryOutcome()
}
```

### Flow

The use case runs the recovery agent **exactly once**:

1. **Assemble recovery instructions** — combine `RecoveryRequest` fields into a focused
   instruction string for the agent. The instructions include:
   - What failed and the error output
   - Current workflow state (which part, which sub-part, iteration)
   - The specific recovery goal
   - Constraints (what the agent must NOT do)

2. **Run recovery agent** — invoke `NonInteractiveAgentRunner.run()`
   (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) with:
   - **Agent type**: `PI` — lightweight, fast, cost-effective for infrastructure fixes
   - **Model**: `$AI_MODEL__ZAI__FAST` (e.g., `glm-4.7-flash`)
   - **Timeout**: 20 minutes

3. **Interpret result**:
   - `NonInteractiveAgentResult.Success` → return `RecoveryOutcome.Resolved`.
   - Agent output contains an escalation marker → return `RecoveryOutcome.Unresolvable(reason)`.
   - `NonInteractiveAgentResult.Failed` or `TimedOut` → log FAIL and return
     `RecoveryOutcome.Unresolvable("recovery_failed")`.

### Constraints for Recovery Agents

Recovery agents receive explicit constraints to prevent them from causing more damage:

- **Do NOT modify agent code or output** — only fix the infrastructure issue
- **Do NOT make commits** — the harness owns all commits
- **Do NOT modify `.ai_out/` contents** — the harness owns this directory
- **Do NOT modify `current_state.json`** — the harness owns workflow state
- **Scope to the specific failure** — do not attempt to "fix" unrelated issues

### Future Consumers

- **Disk space issues** — agent could identify and clean up temporary files
- **Network-related failures** — agent could diagnose connectivity issues
- **Git operation failures** — first consumer, restored from V1 index.lock-only approach
