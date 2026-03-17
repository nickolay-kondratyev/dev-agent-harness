# AgentFacade — Testability Facade / ap.9h0KS4EOK5yumssRCJdbq.E

**Status: PLAN — awaiting alignment before implementation**

---

## Approach Summary

Introduce `AgentFacade` as a **single facade interface** that the orchestration layer
(`PartExecutor`, `TicketShepherd`) uses for **all** interactions with agents: spawn, communicate,
monitor state, kill, and receive signals. The real implementation delegates to existing infra
components (`AgentStarter`, `TmuxSessionManager`, `TmuxCommunicator`, `AgentSessionIdResolver`,
`ContextWindowStateReader`, `SessionsState`). A `FakeAgentFacade` enables comprehensive
unit testing of the orchestration state machine with **virtual time**, keeping integration tests
to sanity checks.

**Why this over keeping raw interfaces:** The orchestration layer's job is to manage a complex
state machine (spawn → wait → iterate → handle timeouts/crashes). Its tests must exercise timing
scenarios, edge cases, and failure modes. Coordinating 5+ fakes that must stay in sync to simulate
one scenario is fragile and obscures test intent. A single facade with a single programmable fake
makes each test case a clear statement of "given this agent behavior, expect this orchestration
result."

**Why now:** The orchestration layer (PartExecutor, TicketShepherd) is about to be implemented.
Establishing the testability seam before implementation ensures the code is designed for
testability from day one, not retrofitted.

---

## Decisions (Resolved)

### D1: Interface granularity → Single facade

**Chosen:** One `AgentFacade` interface with high-level methods.

**Over:**
- Grouped composites (AgentLifecycle + AgentCommunicator + AgentStateReader) — more ISP-pure but
  3 fakes to coordinate in tests, diminishing the testability win.
- Raw interfaces directly — 5+ constructor params per executor, fakes must be coordinated to
  simulate realistic scenarios.

**Tradeoff accepted:** Mild ISP tension (interface has ~5 methods spanning lifecycle, communication,
and state reading). Acceptable because: (a) all methods relate to "interacting with one agent," (b)
the real impl delegates to focused components internally, (c) the primary consumer (PartExecutor)
genuinely needs all of them.

### D2: Signal delivery → Facade owns it

**Chosen:** The facade creates and manages `CompletableDeferred<AgentSignal>` entirely
internally. `PartExecutor` never touches `SessionsState` or a raw deferred directly.

- `spawnAgent()` returns a `SpawnedAgentHandle` (no `Deferred` exposed on the handle).
- `sendPayloadAndAwaitSignal(handle, payload)` is the single entry point for every
  payload delivery + signal receive cycle. It creates a fresh `CompletableDeferred`,
  re-registers the `SessionEntry`, sends the payload with ACK, runs the health-aware
  await loop, and returns the `AgentSignal` to the caller. This resolves **R1** (see below).

**Over:** Signal path stays in SessionsState, PartExecutor uses both AgentFacade (outbound)
and SessionsState (inbound). This would split the agent abstraction and force tests to coordinate
two components.

**Impact on existing specs:** `SessionsState` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) becomes an
**internal implementation detail** of `AgentFacadeImpl`, not a dependency of `PartExecutor`.
The spec still describes the mechanics correctly, but the "Caller" column for `register` changes
from "PartExecutor" to "AgentFacadeImpl." See [Spec Impact](#spec-impact) below.

---

## Interface Shape (Conceptual)

The methods model **what the orchestration layer needs**, not the raw infra operations:

| Method | What it encapsulates | Internal delegation |
|--------|---------------------|---------------------|
| `spawnAgent(config)` | Bootstrap handshake, session ID resolution, initial `SessionsState` registration, TMUX session start | `AgentStarter` + `TmuxSessionManager` + `AgentSessionIdResolver` + `SessionsState` |
| `sendPayloadAndAwaitSignal(handle, payload): AgentSignal` | Full signal lifecycle: create fresh `CompletableDeferred`, re-register `SessionEntry`, send payload with ACK (3× retry), run health-aware await loop, return `AgentSignal` | `TmuxCommunicator` + ACK wrapping + `SessionsState` + `AgentUnresponsiveUseCase` + `ContextWindowStateReader` + `ContextWindowSelfCompactionUseCase` |
| `killSession(handle)` | Kill TMUX session, cleanup | `TmuxSessionManager` |

`SpawnedAgentHandle` contains:
- `guid: HandshakeGuid`
- `sessionId: ResumableAgentSessionId`
- Observable `lastActivityTimestamp` — updated by real impl on every HTTP callback; controlled
  by test in `FakeAgentFacade`

**`sendPayloadAndAwaitSignal` fully encapsulates the signal lifecycle**, including the
health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E). The executor owns no deferred
references and calls no health-ping or context-window methods directly. See
[`PartExecutor.md`](PartExecutor.md) for the loop pseudocode (marked as internal to this method).

---

## Virtual Time Strategy / ap.whDS8M5aD2iggmIjDIgV9.E

The health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) lives **inside
`sendPayloadAndAwaitSignal`** (ref.ap.9h0KS4EOK5yumssRCJdbq.E) and has two timing
dependencies that must be controllable in tests:

### 1. Coroutine delays → `kotlinx-coroutines-test` TestDispatcher

The 1-second tick loop (`awaitSignalWithTimeout(1.second)`) uses coroutine `delay()`/`withTimeout()`.
With `runTest {}` and `TestCoroutineScheduler`, these advance instantly via `advanceTimeBy()`.

**Dependency to add:** `testImplementation(libs.kotlinx.coroutines.test)` (kotlinx-coroutines-test).

### 2. Wall-clock reads → `Clock` interface

The loop compares timestamps: `now() - sessionEntry.lastActivityTimestamp`. A `Clock` (or
`TimeSource`) abstraction injected into `AgentFacadeImpl` allows tests to control "now."

```kotlin
// Production: Clock { Instant.now() }
// Test: TestClock with advanceable time
interface Clock {
    fun now(): Instant
}
```

**Both are needed.** TestDispatcher alone doesn't control `Instant.now()` calls. A Clock alone
doesn't control `delay()` suspension. Together, they give full control over the time dimension.

`Clock` and `HarnessTimeoutConfig` are constructor dependencies of `AgentFacadeImpl` (not
`PartExecutorImpl`). For `FakeAgentFacade`, the test controls signal delivery directly
(see R3) — the loop is not re-executed inside the fake.

### Test shape with virtual time

For **executor tests** (`PartExecutorImpl`) — the fake returns signals on demand:
```
// Pseudocode — illustrates executor-level test
runTest {
    val fake = FakeAgentFacade()
    val executor = PartExecutorImpl(agentFacade = fake, ...)

    // Program the fake: spawn succeeds, then agent sends COMPLETED
    fake.onSpawn { handle }
    fake.onSendPayloadAndAwaitSignal { AgentSignal.Done(DoneResult.COMPLETED) }

    val result = executor.execute()
    result shouldBe PartResult.Completed
}
```

For **facade tests** (`AgentFacadeImpl`) — both virtual time axes are needed to test
the health-aware loop inside `sendPayloadAndAwaitSignal`:
```
// Pseudocode — illustrates facade-level health-loop test
runTest {
    val clock = TestClock(startTime)
    val fakeSessionsState = FakeSessionsState()
    val facade = AgentFacadeImpl(clock = clock, ...)

    launch { signal = facade.sendPayloadAndAwaitSignal(handle, payload) }

    // Advance 31 minutes — past healthTimeouts.normalActivity (30 min)
    clock.advance(31.minutes)
    advanceTimeBy(31.minutes)

    // Verify: facade sent health ping; no response → AgentCrashed
    clock.advance(3.minutes)
    advanceTimeBy(3.minutes)
    signal shouldBe AgentSignal.Crashed(...)
}
```

---

## Requirements

### R1: `AgentFacade` interface spec

A spec document (this file, once promoted from PLAN) that defines:
- The interface contract (methods, parameters, return types)
- `SpawnedAgentHandle` data class
- Relationship to `SessionsState` (internal detail of real impl)
- Thread safety guarantees (suspend, coroutine-safe)
- Error semantics (what happens on spawn failure, ACK exhaustion, etc.)

**Verifiable:** Interface compiles, KDoc describes contract, spec matches implementation.

### R2: `AgentFacadeImpl` (real implementation)

Delegates to existing infra components. No new behavior — wiring only.

**Verifiable:**
- Integration test spawns a real agent through `AgentFacadeImpl`, sends payload, receives ACK
- Covers happy path only — edge cases tested via fake at the orchestration layer

### R3: `FakeAgentFacade` (test double)

Programmable fake for `PartExecutorImpl` unit tests that allows tests to:
- Control spawn behavior (succeed, fail)
- Control `sendPayloadAndAwaitSignal` return value — return any `AgentSignal` variant, optionally
  with a `suspend` delay to simulate latency
- Control `killSession` (record that it was called)
- Verify interactions: was `sendPayloadAndAwaitSignal` called? with what payloads? was session killed?

The fake does **not** re-run the health-aware loop internally — it simply returns the
pre-programmed signal. Health-loop edge cases (timeouts, ping, crash, compaction) are tested
via `AgentFacadeImpl` unit tests with `TestClock` + `TestDispatcher`.

**Verifiable:**
- Unit tests for `FakeAgentFacade` itself (meta-tests that verify the fake behaves correctly)
- Used by `PartExecutorImpl` unit tests — if executor tests pass, the fake is verified implicitly

### R4: `Clock` interface + `TestClock`

- `Clock` interface with `now(): Instant`
- Production `SystemClock` implementation
- `TestClock` with `advance(duration)` for tests
- Injected into `AgentFacadeImpl` (not `PartExecutorImpl`) — only the facade reads wall time
  in its health-aware await loop

**Verifiable:** TestClock unit tests; AgentFacadeImpl health-loop tests use TestClock and verify
timing decisions (ping trigger, crash detection).

### R5: `kotlinx-coroutines-test` dependency

Added to `gradle/libs.versions.toml` and `app/build.gradle.kts` as `testImplementation`.

**Verifiable:** PartExecutor tests use `runTest {}` with `advanceTimeBy()`.

### R6: PartExecutor no longer depends on SessionsState, Clock, or health-monitoring infra directly

PartExecutor takes `AgentFacade` as its sole agent-facing dependency. `SessionsState`,
`Clock`, `AgentUnresponsiveUseCase`, and `ContextWindowStateReader` are **not** in
PartExecutor's constructor — they are constructor dependencies of `AgentFacadeImpl`.

**Verifiable:** PartExecutor constructor signature; PartExecutor unit tests construct with only
`AgentFacade`, `ContextForAgentProvider`, `GitCommitStrategy`, and iteration config — no clock,
no session state, no health-monitoring types.

### R7: Spec updates for downstream impact

Specs that reference PartExecutor → SessionsState relationship must be updated:
- `SessionsState.md` — `register` caller changes from PartExecutor to AgentFacadeImpl
- `PartExecutor.md` — Dependencies section: remove SessionsState, add AgentFacade
- `high-level.md` — if it references the bridge diagram

**Verifiable:** Spec review; no spec references PartExecutor as a direct user of SessionsState.

---

## Gates

### Gate 1: Interface + Spec Alignment

**What:** `AgentFacade` interface defined (Kotlin interface file + updated spec).
`SpawnedAgentHandle` data class defined. `Clock` interface defined. No implementations yet.

**Verify:**
- Code compiles
- Spec review: interface methods match the orchestration layer's needs per PartExecutor.md
- `SessionsState.md` and `PartExecutor.md` updated for the new relationship

**Decision to proceed:** Confirm the interface shape is correct before writing implementations.
Wrong interface shape wastes all downstream work.

### Gate 2: FakeAgentFacade + Virtual Time Foundation

**What:** `FakeAgentFacade` implemented. `TestClock` implemented.
`kotlinx-coroutines-test` added. One proof-of-concept test that uses all three
(fake + TestClock + runTest) to test a simple scenario (e.g., spawn → send → done → completed).

**Verify:**
- Proof-of-concept test passes
- Virtual time works: `advanceTimeBy()` + `TestClock.advance()` together control the time axis
- Fake is ergonomic: test reads clearly, setup is concise

**Decision to proceed:** If virtual time or fake ergonomics don't work, we need to adjust
before building PartExecutor on top of them.

### Gate 3: PartExecutor unit tests (happy path + edge cases)

**What:** `PartExecutorImpl` implemented with unit tests covering both doer-only and
doer+reviewer paths:
- Happy path (spawn → work → done → completed)
- Timeout / crash detection (no activity → ping → no reply → crashed)
- Iteration loop (doer → reviewer → needs_iteration → doer → reviewer → pass)
- ACK failure (payload delivery exhausted → crashed)
- Context window exhaustion (emergency compaction)
- Late fail-workflow detection

**Verify:**
- All unit tests pass with virtual time (no real delays, fast execution)
- Edge cases that would be flaky or slow as integration tests are stable as unit tests

**Decision to proceed:** PartExecutor logic is validated. Ready for AgentFacadeImpl (real
impl) and integration testing.

### Gate 4: AgentFacadeImpl + Integration Sanity

**What:** `AgentFacadeImpl` wired to real infra. One integration test that spawns a
real agent, sends instructions, receives ACK, waits for done signal.

**Verify:**
- Integration test passes (requires Docker, tmux, agent env)
- Real agent → HTTP callback → SessionsState → deferred completion → executor resumes

**Decision to proceed:** Real plumbing works. The unit-tested orchestration logic connects
correctly to real infrastructure.

---

## Risks & Open Questions

### R1: Re-instruction pattern and fresh deferreds ✅ RESOLVED

**Resolved:** `sendPayloadAndAwaitSignal(handle, payload): AgentSignal` creates a fresh
`CompletableDeferred`, re-registers the `SessionEntry` (same `HandshakeGuid`, new deferred),
sends the payload, and runs the health-aware await loop — returning the `AgentSignal` directly.

The executor never sees deferreds. On iteration, it simply calls `sendPayloadAndAwaitSignal`
again with the new instructions payload. Forgetting to reset the deferred is no longer possible
— the method owns that lifecycle unconditionally. Eliminates the silent-hang class of bugs
noted in ticket `nid_0o3dqyqe9tlwpi9uroe9tdqpn_E`.

### R2: `UserQuestionHandler` — Decoupled Q&A Coordinator

User questions are side-channel: the HTTP server enqueues questions into
`SessionEntry.pendingQA` and launches a dedicated **Q&A coordinator** coroutine per session
(ref.ap.NE4puAzULta4xlOLh5kfD.E). The coordinator collects answers via `UserQuestionHandler`
strategy and batch-delivers all answers via `AckedPayloadSender`. This flow goes through
`SessionsState` (for `pendingQA` state) but runs **outside the executor's coroutine scope**.

The Q&A coordinator is independent of `AgentFacade` — it is launched by the server and uses
`AckedPayloadSender` directly for answer delivery. The executor's health-aware await loop
(ref.ap.QCjutDexa2UBDaKB3jTcF.E) only reads `SessionEntry.isQAPending` to suppress health
pings, compaction, and noActivityTimeout during Q&A.

**Impact:** The HTTP server's relationship to AgentFacade is clarified: the server is a
**collaborator** of AgentFacadeImpl (it triggers deferred completion via `SessionsState`) and
the **owner** of the Q&A coordinator coroutine. The Q&A coordinator does not use the
`AgentFacade` interface — it accesses `SessionEntry` and `AckedPayloadSender` directly.

**Must resolve at Gate 1.**

### R4: ISP tension may grow

As V2 features land (parallel agents, auto-recovery, session rotation), the interface may
accumulate methods. Monitor method count. If it exceeds ~8 methods, consider splitting into
focused sub-interfaces that compose into the facade.

**Monitor continuously.** Not blocking.

### R5: Virtual time + real coroutine interactions

`runTest` with `TestDispatcher` can be tricky with production code that launches coroutines
on custom dispatchers (e.g., `Dispatchers.IO`). The executor's use of `DispatcherProvider`
must be injectable so tests can route everything through the test dispatcher.

**Must verify at Gate 2.**

---

## Spec Impact

### Specs to update

| Spec | Change |
|------|--------|
| `PartExecutor.md` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) | Dependencies: replace `SessionsState`, `SpawnTmuxAgentSessionUseCase` with `AgentFacade`. Health-aware await loop: `readContextWindowState` and `sendHealthPing` calls go through `AgentFacade`. |
| `SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) | Ownership: `register` caller → `AgentFacadeImpl` (not PartExecutor). `lookup` caller → `ShepherdServer` (unchanged). Add note: "Internal to `AgentFacadeImpl`; not directly accessed by orchestration layer." |
| `TicketShepherdCreator.md` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) | Wiring: create `AgentFacadeImpl` and pass to executor factories. |
| `SpawnTmuxAgentSessionUseCase.md` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E) | Note: "Encapsulated by `AgentFacadeImpl.spawnAgent()`. Still describes the spawn flow accurately." |

### Specs unchanged

- `agent-to-server-communication-protocol.md` — HTTP protocol unchanged
- `ContextForAgentProvider.md` — instruction assembly unchanged
- `HealthMonitoring.md` — health logic unchanged (just accessed through different interface)
- `high-level.md` — may need minor diagram update

---

## Relationship to Existing Architecture

```
BEFORE (current spec):

  PartExecutor ──► SessionsState ◄── ShepherdServer
       │                                    │
       ├──► SpawnTmuxAgentSessionUseCase     │
       ├──► TmuxCommunicator (ACK)          │
       ├──► ContextWindowStateReader        │
       └──► AgentUnresponsiveUseCase        │

AFTER (with AgentFacade):

  PartExecutor ──► AgentFacade (interface)
                        │
                        ├── AgentFacadeImpl (production)
                        │       ├──► SessionsState ◄── ShepherdServer
                        │       ├──► AgentStarter
                        │       ├──► TmuxSessionManager
                        │       ├──► TmuxCommunicator
                        │       ├──► AgentSessionIdResolver
                        │       └──► ContextWindowStateReader
                        │
                        └── FakeAgentFacade (test)
                                └── Programmable behaviors + virtual time
```

The key insight: **nothing changes below the facade**. All existing infra stays. The facade
is an insertion point that decouples the orchestration layer from the infra layer, enabling
the testability strategy.
