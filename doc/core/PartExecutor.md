# PartExecutor / ap.fFr7GUmCYQEV5SJi8p6AS.E

The abstraction for executing a single Part (one or two sub-parts). Owns the agent spawn →
wait → iterate loop for that part. Shared by both execution parts and the planning phase
(PLANNER↔PLAN_REVIEWER).

---

## Interface

```kotlin
interface PartExecutor {
    suspend fun execute(): PartResult
}
```

A `PartExecutor` is created per part, runs to completion (or failure), and is discarded.
It is **not** reused across parts.

---

## PartResult

Sealed class returned by `execute()`:

```kotlin
sealed class PartResult {
    /** All sub-parts completed successfully */
    object Completed : PartResult()

    /** Agent called /callback-shepherd/fail-workflow */
    data class FailedWorkflow(val reason: String) : PartResult()

    /** Reviewer sent needs_iteration beyond iteration.max and user chose to abort */
    data class FailedToConverge(val summary: String) : PartResult()

    /** Agent crashed — V1: hard stop (no automatic recovery) */
    data class AgentCrashed(val details: String) : PartResult()
}
```

`TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E) reads the `PartResult` and acts accordingly.
All failure variants (`FailedWorkflow`, `FailedToConverge`, `AgentCrashed`) delegate to
`FailedToExecutePlanUseCase` — prints red error, halts. V1 has no automatic crash recovery.
Note: `FailedToConvergeUseCase` runs inside the executor (see DoerReviewerPartExecutor step 9c),
not in TicketShepherd.

---

## AgentSignal / ap.UsyJHSAzLm5ChDLd0H6PK.E

Sealed class representing what flows through the `CompletableDeferred<AgentSignal>` to
the executor. `Done` and `FailWorkflow` are completed by the server; `Crashed` is produced
by the executor's own health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E). This is the
**callback bridge** — the mechanism by which an HTTP callback wakes the suspended executor
coroutine.

```kotlin
sealed class AgentSignal {
    /** Agent called /callback-shepherd/done with a valid result */
    data class Done(val result: DoneResult) : AgentSignal()

    /** Agent called /callback-shepherd/fail-workflow */
    data class FailWorkflow(val reason: String) : AgentSignal()

    /** Executor's health-aware await loop determined the agent has crashed */
    data class Crashed(val details: String) : AgentSignal()
}
```

### DoneResult

```kotlin
enum class DoneResult {
    COMPLETED,        // doer finished work
    PASS,             // reviewer approves
    NEEDS_ITERATION,  // reviewer requests changes
}
```

### What Does NOT Flow Through AgentSignal

| Callback | Why not | Handler |
|----------|---------|---------|
| `/callback-shepherd/started` | Side-channel — startup acknowledgment only (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Updates `lastActivityTimestamp`; confirms agent is alive and env is configured correctly |
| `/callback-shepherd/user-question` | Side-channel — executor stays suspended while Q&A happens | Server delegates to `UserQuestionHandler` (ref.ap.NE4puAzULta4xlOLh5kfD.E), delivers answer via TMUX `send-keys` |
| `/callback-shepherd/ping-ack` | Side-channel — proof of life only | Updates `lastActivityTimestamp`; executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) reads this |

All side-channel callbacks **do** update `SessionEntry.lastActivityTimestamp`
(ref.ap.igClEuLMC0bn7mDrK41jQ.E) so the executor's health-aware await loop knows the agent
is alive. `/callback-shepherd/validate-plan` (ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E) is also a
side-channel — planning-phase only, returns validation result in response body.

### How the Bridge Works

```
Executor                          SessionsState                    Server
   │                                   │                              │
   ├─ create CompletableDeferred       │                              │
   ├─ register SessionEntry ──────────►│                              │
   │  (includes deferred)              │                              │
   ├─ spawn agent, send instructions   │                              │
   ├─ suspend on deferred.await() ─────┤                              │
   │                                   │                              │
   │                                   │  ◄── POST /callback-shepherd/done
   │                                   │      lookup(guid) ──────────►│
   │                                   │      validate result         │
   │                                   │      entry.signalDeferred    │
   │                                   │        .complete(Done(...))  │
   │                                   │                              │
   ◄─ resumes from await() ────────────┤                              │
   │  reads AgentSignal                │                              │
```

---

## Health-Aware Await Loop / ap.QCjutDexa2UBDaKB3jTcF.E

The executor does **not** simply call `signalDeferred.await()`. Instead, it runs a
health-aware await loop that incorporates timeout checks using Kotlin's `withTimeoutOrNull`
/ `select`. This makes the executor the **single owner** of both the signal await and the
health monitoring — no separate background coroutine, no race conditions on the deferred.

Full health monitoring spec: ref.ap.6HIM68gd4kb8D2WmvQDUK.E (HealthMonitoring.md).

### Loop Structure (Pseudocode)

```
while (true) {
    signal = awaitSignalWithTimeout(healthCheckInterval)

    if (signal != null) {
        // Agent sent Done / FailWorkflow via server → deferred completed
        return signal
    }

    // Timeout fired — check if agent is still alive
    elapsed = now() - sessionEntry.lastActivityTimestamp

    if (elapsed < noActivityTimeout) {
        continue  // Activity happened recently, keep waiting
    }

    // No activity within timeout — ping the agent
    NoStatusCallbackTimeOutUseCase.execute(sessionEntry)  // sends ping via TMUX

    // Wait for ping timeout, then re-check activity
    signal = awaitSignalWithTimeout(pingTimeout)

    if (signal != null) {
        return signal  // Agent responded with Done/FailWorkflow during ping window
    }

    if (sessionEntry.lastActivityTimestamp updated during ping window) {
        continue  // Any callback arrived → agent is alive (proof of life)
    }

    // No activity at all during ping window → agent is dead
    NoReplyToPingUseCase.execute(sessionEntry)  // kills TMUX session
    return AgentSignal.Crashed(details)
}
```

### Key Properties

- **Single writer for Crashed**: Only the executor completes the deferred with
  `AgentSignal.Crashed`. The server only completes it with `Done` or `FailWorkflow`.
  No two components race to complete the same deferred.
- **Proof of life, not ping-ack**: After pinging, the executor checks `lastActivityTimestamp`
  for **any** callback, not specifically `/ping-ack`. If the agent sent a `user-question` or
  `validate-plan` during the ping window, it's alive. `/ping-ack` is the fallback for idle
  agents with no other callbacks to send.
- **Scoped to executor lifetime**: The loop runs inside `execute()`. When the executor returns,
  monitoring stops. No orphaned watchers.

### Dependencies (Health Monitoring)

- `NoStatusCallbackTimeOutUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) — sends ping via TMUX
- `NoReplyToPingUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) — kills TMUX, provides crash details
- `SessionEntry.lastActivityTimestamp` (ref.ap.igClEuLMC0bn7mDrK41jQ.E) — updated by server on every callback

---

## SubPartInstructionProvider / ap.4c6Fpv6NjecTyEQ3qayO5.E

Decouples instruction assembly from the executor. The executor does not know whether it is
running a planning part or an execution part — it delegates instruction assembly to this
interface.

```kotlin
interface SubPartInstructionProvider {
    /** Assemble instructions for the doer sub-part */
    suspend fun assembleDoerInstructions(
        iterationNumber: Int,
        reviewerFeedbackPath: Path?,  // null on first iteration
    ): Path

    /** Assemble instructions for the reviewer sub-part */
    suspend fun assembleReviewerInstructions(
        iterationNumber: Int,
        doerOutputPath: Path,
    ): Path
}
```

### Implementations

| Context | Wraps | Notes |
|---------|-------|-------|
| Execution parts | `ContextForAgentProvider.assembleExecutionAgentInstructions()` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) | Role def + ticket + SHARED_CONTEXT.md + prior PUBLIC.md files + callback script usage |
| Planning phase | `ContextForAgentProvider` planner/plan-reviewer methods | Ticket + role catalog for planner; includes `plan.json` for plan-reviewer |

---

## DoerReviewerPartExecutor / ap.mxIc5IOj6qYI7vgLcpQn5.E

Handles parts with two sub-parts (doer + reviewer). Implements the full iteration loop.
The executor coordinates the two agents — at any point during iteration, **only one agent
is actively working** while the other is alive but idle in its TMUX session, waiting for
the executor to send it new instructions.

### Flow

1. **Start doer**:
   a. First iteration: **spawn** doer TMUX session → create `CompletableDeferred` → register
      `SessionEntry` → send instructions → enter health-aware await loop
      (ref.ap.QCjutDexa2UBDaKB3jTcF.E)
   b. Subsequent iterations (re-entry from step 4): doer session **already alive** → create
      fresh `CompletableDeferred` → re-register `SessionEntry` (same HandshakeGuid, new
      deferred) → assemble new instructions (includes reviewer feedback) → send via TMUX
      `send-keys` to **existing** session → enter health-aware await loop
2. **On doer COMPLETED** — start reviewer:
   a. First iteration: **spawn** reviewer TMUX session → create `CompletableDeferred` →
      register `SessionEntry` → send instructions (includes doer's `PUBLIC.md`) → enter
      health-aware await loop
   b. Subsequent iterations (re-entry from step 1b→2): reviewer session **already alive** →
      create fresh `CompletableDeferred` → re-register `SessionEntry` (same HandshakeGuid,
      new deferred) → assemble new instructions (includes doer's updated `PUBLIC.md`) →
      send via TMUX `send-keys` to **existing** session → enter health-aware await loop
3. **On reviewer PASS** → return `PartResult.Completed`
4. **On reviewer NEEDS_ITERATION** → check budget:
   - Within budget → `GitCommitStrategy.onSubPartDone`, increment `iteration.current` →
     go to step 1b (doer re-instruction)
   - Exceeds budget → `FailedToConvergeUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) → user
     decides continue or abort
5. **On FailWorkflow / Crashed** → return corresponding `PartResult`

### Key: Both Sessions Alive, One Active

**Both TMUX sessions stay alive for the entire part lifecycle.** When the doer is working,
the reviewer's TMUX session is alive but idle — waiting for the executor to send new
instructions after the doer completes. When the reviewer is working, the doer's session is
alive but idle — waiting in case the reviewer signals `needs_iteration`.

The executor is the **sole coordinator**: it waits for `done` from the active agent, then
sends instructions to the other agent. No direct agent-to-agent communication. The flow is
always: executor → doer → executor → reviewer → executor → (loop or complete).

Sessions are killed only when the **part** completes (`removeAllForPart`), not between
iterations or between doer↔reviewer transitions. This matches the Hard Constraint: one
TMUX session per sub-part, kept alive across iterations.

### Re-Instruction Pattern

On iteration > 1, both agents already have live TMUX sessions. The executor does NOT
kill/respawn — it:
1. Creates a **fresh** `CompletableDeferred<AgentSignal>`
2. Re-registers the `SessionEntry` (same HandshakeGuid, new deferred)
3. Assembles new instructions via `SubPartInstructionProvider`
4. Sends instruction file path via TMUX `send-keys` to the existing session

This pattern is identical for both doer and reviewer re-instruction.

### Idle Session Death — send-keys Failure

When the executor sends instructions to an idle session via TMUX `send-keys`, the session
may have died while idle (e.g., TMUX killed externally, OOM, terminal crash). This is not
expected in normal operation but must be handled.

**V1 behavior**: If `send-keys` fails (after retry — transient TMUX errors are retried),
the executor logs an **ERROR** with the session name, HandshakeGuid, and the `send-keys`
failure details, then returns `PartResult.AgentCrashed` with a message indicating which
sub-part's idle session died. `TicketShepherd` delegates to `FailedToExecutePlanUseCase`
(red error, halt — waits for human intervention).

No automatic respawn in V1. See `doc_v2/idle-session-recovery.md` for V2 design
(automatic respawn of the dead idle session with `--resume`).

### Git Commits During Execution

The executor receives `GitCommitStrategy` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) as a dependency
and calls `onSubPartDone` after each `AgentSignal.Done` (any result: `COMPLETED`, `PASS`,
`NEEDS_ITERATION`) — before the next sub-part starts or iteration resumes. This is the
mechanism by which V1's `CommitPerSubPart` strategy produces one commit per sub-part signal.

### Dependencies

- `SessionsState` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) — register/lookup sessions
- `SpawnTmuxAgentSessionUseCase` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) — spawn agent sessions
- `SubPartInstructionProvider` (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) — assemble instructions
- `GitCommitStrategy` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) — `onSubPartDone` after each signal
- `FailedToConvergeUseCase` — when iteration budget exceeded
- `NoStatusCallbackTimeOutUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) — ping agent on activity timeout
- `NoReplyToPingUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) — kill TMUX + crash details on ping timeout

---

## SingleDoerPartExecutor

Spawn doer → register → send instructions → enter health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E) → map `AgentSignal` to `PartResult`. No reviewer, no
iteration. Uses the same health-aware await loop as `DoerReviewerPartExecutor` — a single
doer can crash/hang just like a doer-reviewer pair.

### Signal-to-PartResult Mapping

| `AgentSignal` | `PartResult` |
|---------------|-------------|
| `Done(COMPLETED)` | `PartResult.Completed` |
| `FailWorkflow(reason)` | `PartResult.FailedWorkflow(reason)` |
| `Crashed(details)` | `PartResult.AgentCrashed(details)` |

`Done(PASS)` and `Done(NEEDS_ITERATION)` **cannot** reach `SingleDoerPartExecutor` —
the server validates that doers can only send `completed` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E).
If they somehow leak through, treat as a bug — fail with `IllegalStateException`.

---

## Ownership and Lifecycle

- **Created by**: `TicketShepherd` (for execution parts) or `DetailedPlanningUseCase`
  (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) (for the planning part)
- **Run by**: `TicketShepherd` — always controls which executor is active
- **Lifecycle**: one executor per part, created just before execution, discarded after `execute()` returns
- `TicketShepherd` holds an `activeExecutor` reference — the currently running `PartExecutor`.
  This gives cancellation a single reference point. Health monitoring is internal to each
  executor via its health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).
