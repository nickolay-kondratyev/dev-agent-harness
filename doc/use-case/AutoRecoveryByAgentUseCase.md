# AutoRecoveryByAgentUseCase / ap.q54vAxzZnmWHuumhIQQWt.E

A general-purpose recovery mechanism that spawns a coding agent to attempt automated recovery
from infrastructure failures that occur mid-workflow.

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
what needs to happen, spawns a recovery agent, and reports success or failure.

Domain-specific use cases (e.g., `GitOperationFailureUseCase` ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E)
package the failure context and delegate to `AutoRecoveryByAgentUseCase`.

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
   instruction document for the recovery agent. The instructions include:
   - What failed and the error output
   - Current workflow state (which part, which sub-part, iteration)
   - The specific recovery goal
   - Constraints (what the agent must NOT do)
   - The agent should signal completion via `/callback-shepherd/done` with `result: completed`

2. **Spawn recovery agent** — spawn a new TMUX session via `SpawnTmuxAgentSessionUseCase`
   (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) with:
   - **Agent type**: `ClaudeCode` (V1 — only supported agent type)
   - **Model**: `sonnet` (medium tier — cost-effective for infrastructure recovery tasks)
   - A dedicated session name: `recovery__{context_hint}` (e.g., `recovery__git_commit`)
   - The assembled recovery instructions

3. **Await agent signal** — use the same `CompletableDeferred<AgentSignal>` +
   health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) pattern as regular executors,
   but with **shorter timeouts** (recovery should be fast):
   - `noActivityTimeout`: 5 min (vs. 30 min default)
   - `pingTimeout`: 1 min (vs. 3 min default)
   - `maxRecoveryWallClock`: 10 min — overall wall-clock cap for the entire recovery attempt.
     If the agent is still alive but has not completed within 10 minutes, recovery is considered
     failed regardless of activity. Prevents indefinite recovery attempts from agents that keep
     sending ping-acks but never complete.

4. **Interpret result**:
   - `AgentSignal.Done(COMPLETED)` → `RecoveryResult.Success`
   - `AgentSignal.FailWorkflow(reason)` → `RecoveryResult.Failed(reason)`
   - `AgentSignal.Crashed(details)` → `RecoveryResult.Failed(details)`

5. **Kill recovery TMUX session** — always, regardless of outcome. Recovery sessions are
   ephemeral — not kept alive like regular part sessions.

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

- `SpawnTmuxAgentSessionUseCase` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) — spawn recovery agent
- `SessionsState` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) — register recovery session
- Health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) — monitor recovery agent

---

## Not a Retry Mechanism

`AutoRecoveryByAgentUseCase` is NOT a generic retry mechanism. It does not re-run the failed
operation — it spawns an agent to **fix the environment** so the caller can retry. The retry
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
