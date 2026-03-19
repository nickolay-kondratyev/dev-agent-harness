# FakeAgentFacade Implementation Review

## Verdict: PASS_WITH_SUGGESTIONS

The implementation is solid, well-structured, and meets the acceptance criteria. The FakeAgentFacade
is ergonomic, the tests are thorough BDD-style with one assert per `it` block, and the virtual
time proof-of-concept correctly demonstrates both timing axes. Two suggestions below are
non-blocking improvements.

---

## Acceptance Criteria Checklist

| Criterion | Status | Notes |
|-----------|--------|-------|
| FakeAgentFacade compiles and implements AgentFacade | MET | All 4 interface methods implemented |
| PoC test passes using `runTest {}` with FakeAgentFacade + TestClock | MET | Virtual time tests at lines 341-413 |
| Fake is ergonomic: test setup is concise, readable | MET | `onXxx { handler }` pattern is clean |
| Interaction verification works (assert payloads, kill, etc.) | MET | All call lists verified, SendPayloadCall captures both handle and payload |
| `./test.sh` passes | MET | EXIT_CODE=0 |
| PoC test is standalone (no PartExecutorImpl) | MET | No executor references |
| Test (a): fake returns pre-programmed signals | MET | Done, Crashed, SelfCompacted, FailWorkflow all covered |
| Test (b): interaction verification works | MET | spawnCalls, sendPayloadCalls, readContextWindowStateCalls, killSessionCalls all tested |
| Test (c): runTest + TestClock + advanceTimeBy interoperate | MET | Both axes demonstrated independently at lines 368-389 |
| DispatcherProvider is injectable (spec Risk R5) | MET | StandardTestDispatcher test at lines 393-414 |

---

## Issues

### None blocking.

---

## Suggestions

### S1: killSession default silently succeeds -- inconsistent with fail-hard philosophy

**Severity:** Suggestion (non-blocking)

**Location:** `FakeAgentFacade.kt` line 58-60

The `killSessionHandler` defaults to a no-op, while all other handlers default to throwing
`IllegalStateException`. The implementation notes justify this as "kill is often a no-op in tests,"
which is reasonable. However, this is a mild inconsistency with the fail-hard principle stated in
the class-level KDoc ("Handlers default to throwing IllegalStateException so that missing setup is
caught immediately").

**Recommendation:** This is a valid design tradeoff and the KDoc already documents it. Consider
adding a brief note in the KDoc mentioning the killSession exception explicitly, e.g.:

```
 * ...so that missing setup is caught immediately (fail hard, never mask).
 * Exception: `killSession` defaults to recording-only (no-op) since tests rarely need
 * custom kill behavior.
```

This makes the inconsistency intentional and visible rather than surprising.

### S2: Test duplication in sequential signals tests

**Severity:** Suggestion (non-blocking)

**Location:** `FakeAgentFacadeTest.kt` lines 138-179

The two `it` blocks for sequential signals ("first call returns NEEDS_ITERATION" and "second call
returns COMPLETED") duplicate the entire `ArrayDeque` + facade setup. This is understandable given
the one-assert-per-test rule and the Kotest constraint that `describe` blocks are not suspend
contexts.

**Recommendation:** No change needed right now. If more sequential-signal tests are added in the
future, consider extracting a helper that returns a pre-configured facade with the signal queue.

---

## Positive Observations

1. **Clean API design.** The `onXxx { handler }` pattern is more flexible than pre-loaded queues
   and reads naturally. The `ArrayDeque` approach for sequential signals (shown in tests) is a
   good pattern that keeps the fake simple while enabling complex scenarios.

2. **Defensive copy on recorded calls.** The `get()` accessors return `toList()` copies, preventing
   test code from accidentally mutating the internal lists. Good practice.

3. **Correct use of `data class` for SendPayloadCall.** Captures both handle and payload for
   verification, avoids `Pair` per Kotlin standards.

4. **Test isolation.** Each `it` block creates its own `FakeAgentFacade` instance, avoiding shared
   mutable state leaking between tests. The implementation notes explicitly call this out as a
   conscious decision given Kotest's eager `describe` execution.

5. **Virtual time demonstration is clear.** The test at lines 368-389 clearly shows that TestClock
   (wall-clock axis) and coroutine virtual time (delay axis) are independent -- exactly what the
   spec requires for future AgentFacadeImpl health-loop tests.

6. **DispatcherProvider test is minimal but sufficient.** It proves the injection mechanism works
   with `StandardTestDispatcher`, which is all that's needed at the PoC gate.

7. **All `AgentSignal` variants covered.** Tests exercise `Done(COMPLETED)`, `Done(NEEDS_ITERATION)`,
   `Crashed`, `SelfCompacted`, and `FailWorkflow` -- complete coverage of the sealed class.

8. **BDD style consistently applied.** GIVEN/WHEN/THEN nesting is clear, test names are descriptive,
   one assertion per `it` block throughout.
