# AutoRecoveryByAgentUseCase / ap.q54vAxzZnmWHuumhIQQWt.E

A general-purpose recovery mechanism that runs a lightweight non-interactive agent to attempt
automated recovery from infrastructure failures that occur mid-workflow.

---

## Motivation

Certain infrastructure failures (e.g., git commit failures due to disk full, `.gitignore`
conflicts, index lock files) are recoverable — a skilled agent can diagnose the issue,
apply a fix, and allow the workflow to continue. Rather than halting immediately and waiting
for human intervention, the harness first attempts automated recovery via a dedicated agent.

---

## Design

`AutoRecoveryByAgentUseCase` is a **generic recovery executor**. It does not know about git
or any specific failure domain — it receives structured context describing what failed and
what needs to happen, runs a recovery agent, and reports success or failure.

Domain-specific use cases (e.g., `GitOperationFailureUseCase` ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E)
package the failure context and delegate to `AutoRecoveryByAgentUseCase`.

**Uses `NonInteractiveAgentRunner`** (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) — a subprocess-based
agent invocation with no TMUX, no handshake, no health monitoring. Infrastructure recovery
tasks are short-lived and don't need the full interactive session machinery.

---

## Interface

```kotlin
interface AutoRecoveryByAgentUseCase {
    suspend fun attemptRecovery(request: RecoveryRequest): RecoveryResult
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

sealed class RecoveryResult {
    /** Recovery agent completed successfully — retry the original operation */
    object Success : RecoveryResult()

    /** Recovery agent failed or timed out */
    data class Failed(val details: String) : RecoveryResult()
}
```

---

## Flow

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
   - **Timeout**: 5 minutes — infrastructure fixes should be fast. If the agent cannot
     fix it in 5 minutes, it's unlikely to fix it at all.

3. **Interpret result**:
   - `NonInteractiveAgentResult.Success` → `RecoveryResult.Success`
   - `NonInteractiveAgentResult.Failed(exitCode, output)` → `RecoveryResult.Failed(output)`
   - `NonInteractiveAgentResult.TimedOut(output)` → `RecoveryResult.Failed("Recovery timed out after 5 minutes: $output")`

---

## Caller Protocol

Callers (e.g., `GitOperationFailureUseCase`) follow this pattern:

1. Package failure context into `RecoveryRequest`
2. Call `AutoRecoveryByAgentUseCase.attemptRecovery(request)`
3. On `RecoveryResult.Success` → **retry the original operation once**
4. On retry success → continue workflow as normal
5. On retry failure or `RecoveryResult.Failed` → delegate to `FailedToExecutePlanUseCase`
   (prints red error, halts — waits for human intervention)

**Single recovery attempt**: The harness attempts recovery **at most once** per failure.
No retry loops. If the recovery agent cannot fix it, a human needs to intervene.

---

## Constraints for Recovery Agents

Recovery agents receive explicit constraints to prevent them from causing more damage:

- **Do NOT modify agent code or output** — only fix the infrastructure issue
- **Do NOT make commits** — the harness owns all commits
- **Do NOT modify `.ai_out/` contents** — the harness owns this directory
- **Do NOT modify `current_state.json`** — the harness owns workflow state
- **Scope to the specific failure** — do not attempt to "fix" unrelated issues

---

## First Consumer: GitOperationFailureUseCase

See [Git Operation Failure Handling](../core/git.md#git-operation-failure-handling--autorecoverybyagentusecase)
(ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E) for the git-specific use case that delegates to this.

---

## Dependencies

- `NonInteractiveAgentRunner` (ref.ap.ad4vG4G2xMPiMHRreoYVr.E) — run recovery agent as subprocess

---

## Not a Retry Mechanism

`AutoRecoveryByAgentUseCase` is NOT a generic retry mechanism. It does not re-run the failed
operation — it runs an agent to **fix the environment** so the caller can retry. The retry
decision and execution belong to the caller.

---

## Future Consumers

The `AutoRecoveryByAgentUseCase` pattern is designed for extension (OCP). Future infrastructure
failure scenarios can create new domain-specific use cases that delegate to this:

- **TMUX session creation failure** — agent could diagnose TMUX server issues
- **Disk space issues** — agent could identify and clean up temporary files
- **Network-related failures** — agent could diagnose connectivity issues

Each new consumer creates its own use case class, packages domain-specific context, and
delegates to `AutoRecoveryByAgentUseCase`.
