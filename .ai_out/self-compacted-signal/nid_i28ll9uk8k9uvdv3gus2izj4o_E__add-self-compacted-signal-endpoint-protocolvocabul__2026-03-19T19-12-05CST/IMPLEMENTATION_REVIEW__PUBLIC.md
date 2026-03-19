# Implementation Review: SignalCallbackDispatcher

## Summary

**SignalCallbackDispatcher** (ap.olc7abIAv3YNk3PEE92SY.E) maps HTTP signal callbacks to `AgentSignal` variants, performs session lookup via `SessionsState`, completes the signal deferred, and updates `lastActivityTimestamp`. The `SessionEntry.lastActivityTimestamp` field was changed from `val` to `@Volatile var` to enable mutation.

**Overall assessment: APPROVE.** The implementation is clean, well-structured, follows project conventions, and has thorough test coverage. No critical or important issues found. A few minor suggestions below.

### Verification
- `./sanity_check.sh` -- PASS
- `./test.sh` -- PASS (BUILD SUCCESSFUL)
- No pre-existing tests were removed or modified (only `SessionEntry.kt` changed from `val` to `@Volatile var`).

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

None.

---

## Suggestions

### 1. `signalDeferred.complete()` return value is silently ignored

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcher.kt`, line 87

```kotlin
sessionEntry.signalDeferred.complete(agentSignal)
```

`CompletableDeferred.complete()` returns `false` if the deferred was already completed (e.g., duplicate HTTP callback from the agent). Currently this is silently swallowed -- the dispatcher logs `signal_dispatched` and returns `Success` even when the signal had no effect. This is not a bug for V1 (the executor still receives the correct first signal), but consider logging a warning when `complete()` returns `false` to aid debugging duplicate-callback scenarios:

```kotlin
val wasCompleted = sessionEntry.signalDeferred.complete(agentSignal)
if (!wasCompleted) {
    out.warn(
        "signal_dispatch_deferred_already_completed",
        Val(guidValue, ShepherdValType.HANDSHAKE_GUID),
        Val(action, ShepherdValType.SIGNAL_ACTION),
    )
}
```

**Severity:** Low. Acceptable to defer.

### 2. Test duplication -- consider data-driven tests for done result variants

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/server/SignalCallbackDispatcherTest.kt`, lines 64-143

The three `done` signal test blocks (completed, pass, needs_iteration) are structurally identical -- each creates a `SessionsState`, registers a session, dispatches with a different result string, and asserts the corresponding `DoneResult`. Per CLAUDE.md testing standards: "Use data-driven tests to eliminate duplication when testing the same logic with multiple inputs." These three blocks could be collapsed into one data-driven test:

```kotlin
describe("GIVEN a registered session AND done signal") {
    listOf(
        ProtocolVocabulary.DoneResult.COMPLETED to DoneResult.COMPLETED,
        ProtocolVocabulary.DoneResult.PASS to DoneResult.PASS,
        ProtocolVocabulary.DoneResult.NEEDS_ITERATION to DoneResult.NEEDS_ITERATION,
    ).forEach { (protocolResult, expectedDoneResult) ->
        describe("WHEN dispatch(done, result=$protocolResult)") {
            val sessionsState = SessionsState()
            val guid = HandshakeGuid.generate()
            val entry = createTestSessionEntry()
            sessionsState.register(guid, entry)
            val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.DONE,
                payload = mapOf("handshakeGuid" to guid.value, "result" to protocolResult),
            )

            it("THEN returns Success with AgentSignal.Done($expectedDoneResult)") {
                result.shouldBeInstanceOf<DispatchResult.Success>()
                result.signal shouldBe AgentSignal.Done(expectedDoneResult)
            }
        }
    }
}
```

**Severity:** Low. The current form is readable and not wrong -- just verbose.

---

## Correctness Assessment

| Aspect | Verdict |
|--------|---------|
| Signal mapping (`self-compacted` -> `SelfCompacted`, `done` -> `Done`, `fail-workflow` -> `FailWorkflow`) | Correct. Uses `ProtocolVocabulary` constants. |
| `SessionEntry.lastActivityTimestamp` `val` -> `@Volatile var` | Safe. Follows the identical pattern in `SpawnedAgentHandle.lastActivityTimestamp`. Single-writer (dispatcher), read by health monitoring. `@Volatile` guarantees visibility. |
| Thread safety of timestamp + deferred completion | Correct. Timestamp is updated **before** `signalDeferred.complete()`, so any coroutine resuming after `await()` sees the updated timestamp. |
| `DispatchResult` sealed class exhaustiveness | Correct. `when` on sealed class covers all variants without `else`. |
| Constructor injection, clock testability | Correct. `Clock` injected, `OutFactory` injected. |
| Structured logging | Correct. Uses `Val` with `ShepherdValType.HANDSHAKE_GUID` and `ShepherdValType.SIGNAL_ACTION`. Snake_case messages. |
| `DONE_RESULT_MAP` approach | Good workaround for detekt ReturnCount. Map lookup is clean and maintainable. |
| No functionality removed | Confirmed. Only change to existing code is `val` -> `@Volatile var` on `SessionEntry.lastActivityTimestamp`. No tests removed. |

## Documentation Updates Needed

None. The implementation doc (`1_IMPLEMENTATION_WITH_SELF_PLAN__PUBLIC.md`) already captures the design decisions and AP references.
