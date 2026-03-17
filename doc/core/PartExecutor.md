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

    /** Agent called /callback-shepherd/signal/fail-workflow */
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
the executor. `Done`, `FailWorkflow`, and `SelfCompacted` are completed by the server;
`Crashed` is produced by the executor's own health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E). This is the **callback bridge** — the mechanism by
which an HTTP callback wakes the suspended executor coroutine.

```kotlin
sealed class AgentSignal {
    /** Agent called /callback-shepherd/signal/done with a valid result */
    data class Done(val result: DoneResult) : AgentSignal()

    /** Agent called /callback-shepherd/signal/fail-workflow */
    data class FailWorkflow(val reason: String) : AgentSignal()

    /** Executor's health-aware await loop determined the agent has crashed */
    data class Crashed(val details: String) : AgentSignal()

    /** Agent completed self-compaction (ref.ap.HU6KB4uRDmOObD54gdjYs.E) */
    object SelfCompacted : AgentSignal()
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
| `/callback-shepherd/signal/started` | Side-channel signal — startup acknowledgment only (ref.ap.xVsVi2TgoOJ2eubmoABIC.E) | Updates `lastActivityTimestamp`; confirms agent is alive and env is configured correctly |
| `/callback-shepherd/signal/user-question` | Side-channel signal — executor stays suspended while Q&A happens | Server delegates to `UserQuestionHandler` (ref.ap.NE4puAzULta4xlOLh5kfD.E), delivers answer via `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E). Duplicate questions deduplicated at the server (ref.ap.Girgb4gaq2aecYTHjUj8a.E). |
| `/callback-shepherd/signal/ping-ack` | Side-channel signal — proof of life only | Updates `lastActivityTimestamp`; executor's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) reads this |
| `/callback-shepherd/signal/ack-payload` | Side-channel signal — payload delivery confirmation (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E) | Updates `lastActivityTimestamp`; clears `pendingPayloadAck` on `SessionEntry`; executor's ACK-await phase reads this |

All side-channel signals **do** update `SessionEntry.lastActivityTimestamp`
(ref.ap.igClEuLMC0bn7mDrK41jQ.E) so the executor's health-aware await loop knows the agent
is alive. `/callback-shepherd/query/validate-plan` (ref.ap.R8mNvKx3wQ5pLfYtJ7dZe.E) is also
a non-lifecycle endpoint (Tier 2 query) — planning-phase only, returns validation result in
response body. It also updates `lastActivityTimestamp`.

### How the Bridge Works

```
Executor                   AgentFacade              SessionsState         Server
   │                            │                              │                  │
   ├─ spawnAgent(config) ──────►│                              │                  │
   │                            ├─ create CompletableDeferred  │                  │
   │                            ├─ register SessionEntry ─────►│                  │
   │                            ├─ spawn TMUX, bootstrap       │                  │
   │                            ├─ return SpawnedAgentHandle   │                  │
   ◄─ handle (with Deferred) ──┤  (includes signal Deferred)  │                  │
   │                            │                              │                  │
   ├─ suspend on handle         │                              │                  │
   │    .signal.await() ────────┤                              │                  │
   │                            │                              │ ◄── POST /done   │
   │                            │                              │  lookup(guid) ──►│
   │                            │                              │  validate result │
   │                            │                              │  entry.signal    │
   │                            │                              │  .complete(Done) │
   │                            │                              │                  │
   ◄─ resumes from await() ────┤                              │                  │
   │  reads AgentSignal         │                              │                  │
```

In tests, `FakeAgentFacade` replaces the middle three columns — the test directly
controls when the deferred is completed and with what `AgentSignal`.

---

## Health-Aware Await Loop / ap.QCjutDexa2UBDaKB3jTcF.E

The executor does **not** simply call `signalDeferred.await()`. Instead, it runs a
health-aware await loop that incorporates timeout checks using Kotlin's `withTimeoutOrNull`
/ `select`. This makes the executor the **single owner** of both the signal await and the
health monitoring — no separate background coroutine, no race conditions on the deferred.

Full health monitoring spec: ref.ap.6HIM68gd4kb8D2WmvQDUK.E (HealthMonitoring.md).

### Loop Structure (Pseudocode)

The loop has two phases: **ACK-await** (confirms payload delivery) and **signal-await**
(waits for agent to complete work). The ACK-await phase is handled by `AckedPayloadSender`
(ref.ap.tbtBcVN2iCl1xfHJthllP.E) after every `send-keys` delivery
(ref.ap.r0us6iYsIRzrqHA5MVO0Q.E). The pseudocode below shows the full flow for context:

```
// Phase A: ACK-Await — confirm payload delivery
// (skipped if no payload was sent, e.g., on initial spawn where bootstrap ACK is /signal/started)
ackAttempt = 0
while (sessionEntry.pendingPayloadAck != null) {
    ackAttempt++
    if (ackAttempt > 3) {
        // All retries exhausted — agent alive but not processing input
        kill TMUX session
        return AgentSignal.Crashed("Payload delivery failed after 3 attempts")
    }
    if (ackAttempt > 1) {
        // Retry: re-send the same wrapped payload via send-keys
        resendPayloadViaSendKeys(wrappedPayload)
    }
    waitForAck(ackTimeout = 3 minutes, sessionEntry)
}

// Phase B: Signal-Await with Health Monitoring + Context Window Compaction
//
// PRECONDITION: This phase runs AFTER the bootstrap handshake (/signal/started received)
// and AFTER contextWindowStateReader.validatePresence() has confirmed
// context_window_slim.json exists. The file is guaranteed present.
//
// Liveness: determined solely by lastActivityTimestamp (HTTP callbacks).
// Compaction: determined by context_window_slim.json (remaining_percentage).
// These are independent concerns — see HealthMonitoring.md (ref.ap.dnc1m7qKXVw2zJP8yFRE.E).
//
lastHealthCheck = now()
while (true) {
    signal = awaitSignalWithTimeout(1.second)

    if (signal != null) {
        // Agent sent Done / FailWorkflow / SelfCompacted via server → deferred completed
        return signal
    }

    // --- Read context window state for compaction decisions (every ~1 second) ---
    contextState = contextWindowStateReader.read(agentSessionId)
    if (contextState.remainingPercentage <= HARD_THRESHOLD) {
        if (signalDeferred.isCompleted) {
            return signalDeferred.await()
        }
        // Emergency compaction — see ContextWindowSelfCompactionUseCase
        // (ref.ap.8nwz2AHf503xwq8fKuLcl.E)
        return handleEmergencyCompaction(sessionEntry)
    }

    // --- Health check: lastActivityTimestamp only ---
    if (now() - lastHealthCheck >= healthCheckInterval) {
        lastHealthCheck = now()
        callbackAge = now() - sessionEntry.lastActivityTimestamp

        if (callbackAge >= noActivityTimeout) {
            log.warn("last_activity_stale — triggering ping",
                Val(callbackAge, STALE_DURATION),
                Val(noActivityTimeout, NO_ACTIVITY_TIMEOUT))

            // --- Send ping and check for proof of life ---
            prePingCallbackTimestamp = sessionEntry.lastActivityTimestamp

            log.info("sending_health_ping",
                Val(sessionEntry.tmuxAgentSession.tmuxSession.name, TMUX_SESSION_NAME),
                Val(callbackAge, STALE_DURATION))
            AgentUnresponsiveUseCase.execute(sessionEntry, DetectionContext.NO_ACTIVITY_TIMEOUT)  // sends ping via TMUX

            // Wait for ping timeout, then re-check
            signal = awaitSignalWithTimeout(pingTimeout)

            if (signal != null) {
                return signal  // Agent responded with Done/FailWorkflow during ping window
            }

            // Did any callback arrive during the ping window?
            callbackAdvanced = sessionEntry.lastActivityTimestamp > prePingCallbackTimestamp

            if (callbackAdvanced) {
                log.info("proof_of_life_after_ping")
                continue  // Agent is alive — loop back to monitoring
            }

            // No callback → agent is dead
            log.error("crash_detected — no activity after ping",
                Val(sessionEntry.tmuxAgentSession.tmuxSession.name, TMUX_SESSION_NAME))
            AgentUnresponsiveUseCase.execute(sessionEntry, DetectionContext.PING_TIMEOUT)  // kills TMUX session
            return AgentSignal.Crashed(details)
        }
    }
}
```

### Key Properties

- **Single writer for Crashed**: Only the executor completes the deferred with
  `AgentSignal.Crashed`. The server only completes it with `Done` or `FailWorkflow`.
  No two components race to complete the same deferred.
- **Callbacks-only liveness**: After pinging, the executor checks only
  `lastActivityTimestamp` for advancement. If any callback arrived during the ping window,
  the agent is alive. Simple 2-case model (fresh callback / stale callback).
  See HealthMonitoring.md (ref.ap.dnc1m7qKXVw2zJP8yFRE.E) for the simplification tradeoff.
- **Separate compaction concern**: `context_window_slim.json` is polled every ~1 second for
  `remaining_percentage` to drive compaction decisions (ref.ap.8nwz2AHf503xwq8fKuLcl.E).
  This is independent of liveness — compaction checks run regardless of callback freshness.
- **Scoped to executor lifetime**: The loop runs inside `execute()`. When the executor returns,
  monitoring stops. No orphaned watchers.
- **Full audit trail**: Every health check decision is logged with structured values
  (ref.ap.RJWVLgUGjO5zAwupNLhA0.E — Logging Principle).

### Dependencies (Health Monitoring)

- `AgentUnresponsiveUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) — parameterized by `DetectionContext`: sends ping (`NO_ACTIVITY_TIMEOUT`), kills TMUX + provides crash details (`PING_TIMEOUT`, `STARTUP_TIMEOUT`)
- `SessionEntry.lastActivityTimestamp` (ref.ap.igClEuLMC0bn7mDrK41jQ.E) — updated by server on every callback (sole liveness signal)
- `ContextWindowStateReader` (ref.ap.ufavF1Ztk6vm74dLAgANY.E) — reads `remaining_percentage` for compaction decisions only (ref.ap.8nwz2AHf503xwq8fKuLcl.E); NOT used for liveness

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
      deferred) → assemble new instructions (includes reviewer feedback) → deliver via
      `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E) to **existing** session →
      enter health-aware await loop
2. **On doer COMPLETED** — **PUBLIC.md validation** (ref.ap.THDW9SHzs1x2JN9YP9OYU.E):
   verify doer's `comm/out/PUBLIC.md` exists and is non-empty. If missing/empty → trigger
   re-instruction (see [PUBLIC.md Validation After Done](#publicmd-validation-after-done--apthdw9shzs1x2jn9yp9oyue)).
   Then → start reviewer:
   a. First iteration: **spawn** reviewer TMUX session → create `CompletableDeferred` →
      register `SessionEntry` → send instructions (includes doer's `PUBLIC.md`) → enter
      health-aware await loop
   b. Subsequent iterations (re-entry from step 1b→2): reviewer session **already alive** →
      create fresh `CompletableDeferred` → re-register `SessionEntry` (same HandshakeGuid,
      new deferred) → assemble new instructions (includes doer's updated `PUBLIC.md`) →
      deliver via `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E) to **existing**
      session → enter health-aware await loop
3. **On reviewer PASS** — **PUBLIC.md validation** (ref.ap.THDW9SHzs1x2JN9YP9OYU.E):
   verify reviewer's `comm/out/PUBLIC.md` exists and is non-empty. If missing/empty → trigger
   re-instruction. Then **feedback completion guard**
   (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E): validate `__feedback/pending/` contains no `critical__*`
   or `important__*` files. If found → re-instruct reviewer (one retry, then `AgentCrashed`).
   Remaining `optional__*` files do not block — harness moves them to `addressed/` on
   completion. Otherwise → return `PartResult.Completed`
4. **On reviewer NEEDS_ITERATION** — **PUBLIC.md validation** (ref.ap.THDW9SHzs1x2JN9YP9OYU.E):
   verify reviewer's `comm/out/PUBLIC.md` exists and is non-empty. If missing/empty → trigger
   re-instruction. Then → check budget:
   - Within budget → `GitCommitStrategy.onSubPartDone`, increment `iteration.current` →
     **Granular Feedback Loop** (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E):
     - **Feedback files presence guard**: validate reviewer wrote feedback files to
       `__feedback/pending/` (ref.ap.3Hskx3JzhDlixTnvYxclk.E). Empty → re-instruct
       reviewer (one retry, then `AgentCrashed`).
     - **Inner feedback loop**: process feedback items one at a time — critical → important
       → optional (severity determined by filename prefix). Each item: self-compaction check
       → doer re-instruction with single item → await done → harness reads `## Resolution:`
       marker from feedback file → `ADDRESSED`: harness moves to `addressed/`, git commit;
       `REJECTED`: harness delegates to `RejectionNegotiationUseCase`
       (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) — focused inline resolution while both agents have
       the item in context (bounded at 2 disagreement rounds, reviewer is authority).
       Missing marker → re-instruct doer (one retry, then `AgentCrashed`).
       Full flow detail in ref.ap.5Y5s8gqykzGN1TVK5MZdS.E.
     - After inner loop: validate `pending/` contains no `critical__*` or `important__*`
       files → re-instruct reviewer → go to step 3 (PASS) or step 4 (NEEDS_ITERATION)
   - Exceeds budget → `FailedToConvergeUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) → user
     decides continue or abort
5. **On FailWorkflow / Crashed** → return corresponding `PartResult`

### PUBLIC.md Validation After Done / ap.THDW9SHzs1x2JN9YP9OYU.E

After every `AgentSignal.Done` (any result: `COMPLETED`, `PASS`, `NEEDS_ITERATION`), the
executor verifies that the signaling agent's `comm/out/PUBLIC.md` exists and is non-empty
**before** proceeding to the next step (reviewer start, iteration restart, or part completion).

**Why this matters:** `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) assembles
the next agent's instructions including prior `PUBLIC.md` files. If the doer signals `done`
without writing `PUBLIC.md` (or writes it to the wrong path), the reviewer receives empty
context — wasting an iteration or producing a meaningless review. Catching this immediately
after `done` prevents downstream corruption.

**Validation logic:**

1. Resolve the expected path: `comm/out/PUBLIC.md` for the sub-part that just signaled `done`
   (path resolved via the `.ai_out/` directory schema — ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
2. Check: file exists AND file size > 0 bytes
3. If **valid** → proceed to next step (reviewer start, iteration restart, etc.)
4. If **missing or empty** → re-instruction attempt (one retry):
   a. Log **WARN** identifying the sub-part and the missing/empty `PUBLIC.md` path
   b. Create fresh `CompletableDeferred<AgentSignal>` → re-register `SessionEntry`
      (same HandshakeGuid, new deferred)
   c. Deliver a re-instruction message via `AckedPayloadSender`
      (ref.ap.tbtBcVN2iCl1xfHJthllP.E) telling the agent:
      - Its `PUBLIC.md` at `<path>` is missing or empty
      - It must write `PUBLIC.md` with its work log (decisions, rationale, what was done)
      - It must re-signal `done` with the same result value
   d. Enter health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) waiting for the
      re-signal
   e. On re-signal received → re-validate `PUBLIC.md`
   f. If **still** missing or empty after retry → return `PartResult.AgentCrashed` with a
      message: `"Agent failed to produce PUBLIC.md after explicit re-instruction"`.
      `TicketShepherd` delegates to `FailedToExecutePlanUseCase` (red error, halt).

**One retry, not infinite:** If the agent cannot produce `PUBLIC.md` after being explicitly
told it is missing, something is fundamentally wrong (misconfigured output path, agent
context exhausted, agent confused). Retrying further would waste time.

**Re-instruction message format** (sent as a direct `send-keys` message, not an instruction
file — short enough for inline delivery):

```
Your PUBLIC.md at <path> is missing or empty. You MUST write your work log to this file
before signaling done. Write PUBLIC.md now, then re-signal:
callback_shepherd.signal.sh done <result>
```

**Applies to all executor types:** Both `DoerReviewerPartExecutor` and
`SingleDoerPartExecutor` perform this validation. The check is identical — only the
sub-part path differs.

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
4. Delivers the instruction file path via `AckedPayloadSender`
   (ref.ap.tbtBcVN2iCl1xfHJthllP.E) to the existing TMUX session
5. On ACK received, enters health-aware signal-await loop
   (ref.ap.QCjutDexa2UBDaKB3jTcF.E)

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
`NEEDS_ITERATION`) — after PUBLIC.md validation (ref.ap.THDW9SHzs1x2JN9YP9OYU.E) succeeds
but before the next sub-part starts or iteration resumes. This ordering ensures the commit
captures a validated `PUBLIC.md`. This is the mechanism by which V1's `CommitPerSubPart`
strategy produces one commit per sub-part signal.

### Dependencies

- **`AgentFacade`** (ref.ap.9h0KS4EOK5yumssRCJdbq.E) — single facade for all agent
  operations: spawn, send payload with ACK, health ping, read context window state, kill session.
  Replaces direct dependencies on `SessionsState`, `SpawnTmuxAgentSessionUseCase`,
  `TmuxCommunicator`, and `ContextWindowStateReader`. Signal delivery flows through
  `SpawnedAgentHandle.signal` (a `Deferred<AgentSignal>`). See
  [`AgentFacade`](AgentFacade.md) for the full interface spec.
- `SubPartInstructionProvider` (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E) — assemble instructions
- `GitCommitStrategy` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) — `onSubPartDone` after each signal
- `Clock` (ref.ap.whDS8M5aD2iggmIjDIgV9.E) — wall-clock abstraction for timestamp comparisons
  in the health-aware await loop. Production: `SystemClock`. Tests: `TestClock` with virtual time.
- `FailedToConvergeUseCase` — when iteration budget exceeded
- **Granular Feedback Loop** (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) — inner feedback loop, per-item doer re-instruction, feedback file guards, part completion guard. Full spec: [`doc/plan/granular-feedback-loop.md`](../plan/granular-feedback-loop.md)

### Testability

Both executor implementations are unit-tested via `FakeAgentFacade` + virtual time
(`TestClock` + `kotlinx-coroutines-test`). This enables deterministic testing of the full
state machine — including timing-sensitive health monitoring, timeout/crash detection, and
iteration edge cases — without real TMUX sessions or real agents. See
[Testing Strategy](../high-level.md#testing-strategy--fake-driven-unit-coverage) in high-level.md.

---

## SingleDoerPartExecutor

Spawn doer → register → send instructions → enter health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E) → **PUBLIC.md validation**
(ref.ap.THDW9SHzs1x2JN9YP9OYU.E) → map `AgentSignal` to `PartResult`. No reviewer, no
iteration. Uses the same health-aware await loop as `DoerReviewerPartExecutor` — a single
doer can crash/hang just like a doer-reviewer pair.

### Signal-to-PartResult Mapping

| `AgentSignal` | `PartResult` |
|---------------|-------------|
| `Done(COMPLETED)` | After PUBLIC.md validation → `PartResult.Completed` |
| `FailWorkflow(reason)` | `PartResult.FailedWorkflow(reason)` |
| `Crashed(details)` | `PartResult.AgentCrashed(details)` |

`Done(PASS)` and `Done(NEEDS_ITERATION)` **cannot** reach `SingleDoerPartExecutor` —
the server validates that doers can only send `completed` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E).
If they somehow leak through, treat as a bug — fail with `IllegalStateException`.

### Git Commits

Same as `DoerReviewerPartExecutor` — calls `GitCommitStrategy.onSubPartDone`
(ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) after the doer signals completion.

### Dependencies

Same as `DoerReviewerPartExecutor`: `AgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E),
`GitCommitStrategy` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E), `Clock`
(ref.ap.whDS8M5aD2iggmIjDIgV9.E). No `SubPartInstructionProvider` for reviewer, no
`FailedToConvergeUseCase` (no iteration).

---

## Ownership and Lifecycle

- **Created by**: `TicketShepherd` (for execution parts) or `DetailedPlanningUseCase`
  (ref.ap.cJhuVZTkwfrWUzTmaMbR3.E) (for the planning part)
- **Run by**: `TicketShepherd` — always controls which executor is active
- **Lifecycle**: one executor per part, created just before execution, discarded after `execute()` returns
- `TicketShepherd` holds an `activeExecutor` reference — the currently running `PartExecutor`.
  This gives cancellation a single reference point. Health monitoring is internal to each
  executor via its health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).
