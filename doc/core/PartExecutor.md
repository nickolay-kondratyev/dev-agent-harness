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

    /** Agent crashed and could not be recovered */
    data class AgentCrashed(val details: String) : PartResult()
}
```

`TicketShepherd` (ref.ap.P3po8Obvcjw4IXsSUSU91.E) reads the `PartResult` and delegates to
the appropriate use case (`FailedToExecutePlanUseCase`, `FailedToConvergeUseCase`, etc.).

---

## AgentSignal / ap.UsyJHSAzLm5ChDLd0H6PK.E

Sealed class representing what flows from the server (or health monitor) to the executor
via `CompletableDeferred<AgentSignal>`. This is the **callback bridge** — the mechanism by
which an HTTP callback wakes the suspended executor coroutine.

```kotlin
sealed class AgentSignal {
    /** Agent called /callback-shepherd/done with a valid result */
    data class Done(val result: DoneResult) : AgentSignal()

    /** Agent called /callback-shepherd/fail-workflow */
    data class FailWorkflow(val reason: String) : AgentSignal()

    /** Health monitor determined the agent has crashed */
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
| `/callback-shepherd/user-question` | Side-channel — executor stays suspended while Q&A happens | Server handles directly: presents to human, delivers answer via TMUX `send-keys` |
| `/callback-shepherd/ping-ack` | Side-channel — resets health timer only | Health monitor resets its timer |

Both side-channel callbacks **do** update `SessionEntry.lastActivityTimestamp`
(ref.ap.igClEuLMC0bn7mDrK41jQ.E) so the health monitor knows the agent is alive.

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

### Flow

```
1. Spawn doer TMUX session (via SpawnTmuxAgentSessionUseCase)
2. Create CompletableDeferred<AgentSignal> for doer
3. Register SessionEntry in SessionsState (includes deferred)
4. Assemble doer instructions (via SubPartInstructionProvider, iteration=1, no feedback)
5. Send instructions via TMUX send-keys
6. Suspend on deferred.await()
7. On Done(COMPLETED):
   a. Spawn reviewer TMUX session
   b. Create CompletableDeferred<AgentSignal> for reviewer
   c. Register SessionEntry for reviewer
   d. Assemble reviewer instructions (via SubPartInstructionProvider)
   e. Send instructions via TMUX send-keys
   f. Suspend on reviewer deferred.await()
8. On reviewer Done(PASS) → return PartResult.Completed
9. On reviewer Done(NEEDS_ITERATION):
   a. Check iteration counter vs iteration.max
   b. If within budget:
      - Increment iteration counter
      - Create FRESH CompletableDeferred for doer
      - Re-register SessionEntry (same GUID, new deferred)
      - Assemble new doer instructions (with reviewer feedback path)
      - Send via TMUX send-keys to EXISTING doer session
      - Suspend on doer deferred → on Done(COMPLETED) → resume reviewer (same pattern)
   c. If exceeds budget → delegate to FailedToConvergeUseCase → return PartResult.FailedToConverge
10. On FailWorkflow → return PartResult.FailedWorkflow
11. On Crashed → return PartResult.AgentCrashed
```

### Key: TMUX Sessions Stay Alive Across Iterations

On `needs_iteration`, no TMUX sessions are killed. The executor:
1. Creates a **fresh** `CompletableDeferred` for the doer
2. Re-registers the `SessionEntry` in `SessionsState` (same HandshakeGuid, new deferred)
3. Assembles new doer instructions (with reviewer feedback) via `SubPartInstructionProvider`
4. Sends instructions via TMUX `send-keys` to the **existing** doer session
5. Suspends on the new deferred

Same pattern for resuming the reviewer after the doer completes the iteration.

### Dependencies

- `SessionsState` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) — register/lookup sessions
- `SpawnTmuxAgentSessionUseCase` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) — spawn agent sessions
- `SubPartInstructionProvider` (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) — assemble instructions
- `FailedToConvergeUseCase` — when iteration budget exceeded

---

## SingleDoerPartExecutor

Handles parts with a single sub-part (doer only). Trivial — no iteration loop.

### Flow

```
1. Spawn doer TMUX session
2. Create CompletableDeferred<AgentSignal>
3. Register SessionEntry in SessionsState
4. Assemble doer instructions (via SubPartInstructionProvider, iteration=1, no feedback)
5. Send instructions via TMUX send-keys
6. Suspend on deferred.await()
7. On Done(COMPLETED) → return PartResult.Completed
8. On FailWorkflow → return PartResult.FailedWorkflow
9. On Crashed → return PartResult.AgentCrashed
```

No reviewer, no iteration. If the single sub-part is a doer, `COMPLETED` is the only valid
`Done` result.

---

## Ownership and Lifecycle

- **Created by**: `TicketShepherd` (for execution parts) or `DetailedPlanningUseCase`
  (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) (for the planning part)
- **Run by**: `TicketShepherd` — always controls which executor is active
- **Lifecycle**: one executor per part, created just before execution, discarded after `execute()` returns
- `TicketShepherd` holds an `activeExecutor` reference — the currently running `PartExecutor`.
  This gives health monitoring and cancellation a single reference point.
