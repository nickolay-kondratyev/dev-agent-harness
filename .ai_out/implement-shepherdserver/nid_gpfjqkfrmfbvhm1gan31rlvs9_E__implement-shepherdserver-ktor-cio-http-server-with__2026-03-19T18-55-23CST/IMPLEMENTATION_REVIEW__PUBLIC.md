# ShepherdServer Implementation Review

## Summary

The implementation adds `ShepherdServer` -- an embedded Ktor CIO HTTP server handling 6 agent-to-harness signal callbacks. The code is well-structured, follows project conventions (constructor injection, structured logging, BDD tests), and all 1035+ tests pass including 21 new ShepherdServer tests. Detekt and sanity checks pass clean.

Overall assessment: **Solid implementation. One important issue and a few suggestions below.**

## No CRITICAL Issues

No security, correctness, or data loss issues found.

- No hardcoded secrets or credentials.
- No injection vulnerabilities -- all inputs are deserialized from typed DTOs.
- No removed tests or use cases.
- No resource leaks (Ktor test application handles lifecycle).

## One IMPORTANT Issue

### 1. `self-compacted` is a lifecycle signal but spec says it should also complete `signalDeferred`

Looking at the spec and the implementation together, this is actually handled correctly. The implementation completes `signalDeferred` with `AgentSignal.SelfCompacted` for the `self-compacted` endpoint (line 226 of `ShepherdServer.kt`). This is consistent with the spec which lists `self-compacted` alongside `done` and `fail-workflow` as a lifecycle signal. No issue here upon closer inspection.

### 2. Duplicate `createTestTmuxAgentSession` / `createTestSessionEntry` helpers across test files (DRY violation)

Three test files define their own private copies of nearly identical test helper functions:

- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionTestFixtures.kt` (the shared fixture)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCaseTest.kt` (private copies)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSenderTest.kt` (private copies)

The `QaDrainAndDeliverUseCaseTest.kt` and `AckedPayloadSenderTest.kt` files have their own `noOpCommunicator`, `noOpExistsChecker`, `createTestTmuxAgentSession()`, and `createTestSessionEntry()` -- these are duplicates of the shared `SessionTestFixtures.kt`. This pre-dates the current PR (the `QaDrainAndDeliverUseCaseTest.kt` duplication existed before), but the new `ShepherdServerTest.kt` correctly uses the shared `createTestSessionEntry()` from `SessionTestFixtures.kt`, which is good.

**Recommendation**: Since the CLAUDE.md says "DRY -- Eliminate knowledge duplication" and also says "When adjusting the codebase, look for opportunities" -- consider creating a follow-up ticket to consolidate the pre-existing duplication in `QaDrainAndDeliverUseCaseTest.kt` and `AckedPayloadSenderTest.kt` to use the shared `SessionTestFixtures`. This is pre-existing and not blocking, but worth tracking. The new code in this PR does NOT introduce new duplication -- it reuses the shared fixtures correctly.

## Suggestions

### 1. Jackson deserialization: missing fields produce cryptic errors

The request DTOs (in `SignalRequests.kt`) use Jackson for deserialization. If a required field is missing (e.g., agent sends `{}` without `handshakeGuid`), Jackson will throw a `MissingKotlinParameterException` which Ktor will convert to a 500 response with a stack trace, rather than a clean 400.

Consider installing Ktor's `StatusPages` plugin to catch Jackson deserialization errors and return 400 with a clear message. This is a robustness improvement, not a correctness bug -- the agent callback scripts always send complete payloads.

```kotlin
install(StatusPages) {
    exception<MissingKotlinParameterException> { call, cause ->
        call.respondText(
            "Missing required field: ${cause.parameter.name}",
            status = HttpStatusCode.BadRequest,
        )
    }
}
```

### 2. The `ack-payload` handler does not use `compareAndSet` for thread safety

At line 271 of `ShepherdServer.kt`:
```kotlin
entry.pendingPayloadAck.set(null)
```

The read (`.get()` at line 252) and write (`.set(null)` at line 271) are not atomic together. Between the `get()` and the `set(null)`, another thread could change `pendingPayloadAck`. In practice this is very unlikely because the only other writer is `AckedPayloadSenderImpl` setting a new payload ID before sending, and that happens in a different phase. But for correctness, `compareAndSet(pendingId, null)` would be cleaner:

```kotlin
val cleared = entry.pendingPayloadAck.compareAndSet(pendingId, null)
if (cleared) {
    // log success
} else {
    // log that the value changed between check and clear
}
```

This is a minor theoretical concern, not a practical bug in the current architecture.

### 3. Test coverage gap: no test for `self-compacted` duplicate signal

There are duplicate signal tests for `/signal/done` (line 193-217) and for late `/signal/fail-workflow` after done (line 257-305), but no test for duplicate `/signal/self-compacted`. This is a minor gap since the code path is the same pattern (CompletableDeferred.complete returns false), but for completeness it would be nice to have.

### 4. `SIGNAL_ACTION` ValType defined but unused

`ShepherdValType.SIGNAL_ACTION` (line 43-46 of `ShepherdValType.kt`) is defined but never used in the implementation. If it was planned for future use, consider adding a comment. If not, it is dead code.

## Spec Compliance Checklist

| Criterion | Status | Notes |
|-----------|--------|-------|
| 6 endpoints implemented | PASS | started, done, user-question, fail-workflow, self-compacted, ack-payload |
| DOER accepts only `completed` | PASS | `isResultValidForRole` enforces this |
| REVIEWER accepts only `pass`/`needs_iteration` | PASS | `isResultValidForRole` enforces this |
| Duplicate signals return 200 + WARN | PASS | CompletableDeferred.complete returns false, logs WARN |
| Late fail-workflow after done returns 200 + ERROR | PASS | Explicit ERROR log level on line 203 |
| 404 for unknown GUID | PASS | `lookupOrRespond404` returns 404 with WARN |
| 400 for invalid result | PASS | Both invalid string and role mismatch return 400 |
| `lastActivityTimestamp` updated on all callbacks | PASS | `updateTimestamp(entry)` called in every handler |
| `questionQueue` appended on user-question | PASS | `entry.questionQueue.add(questionContext)` |
| `pendingPayloadAck` cleared on matching ack-payload | PASS | Correct matching + clear logic |
| Mismatched payloadId: 200 + WARN, NOT cleared | PASS | Correct behavior with WHY-NOT comment |
| Duplicate ACK (null pending): 200 + WARN | PASS | Handled at line 254-259 |

## Code Quality Checklist

| Criterion | Status | Notes |
|-----------|--------|-------|
| Constructor injection | PASS | `sessionsState` and `outFactory` injected via constructor |
| Structured logging with Val/ValType | PASS | All log statements use `Val(value, ShepherdValType.X)` |
| No free-floating functions | PASS | All functions are instance methods or companion object members |
| SRP | PASS | ShepherdServer handles HTTP routing only; validation logic is well-separated into companion helpers |
| BDD GIVEN/WHEN/THEN tests | PASS | Clear structure throughout |
| One assert per `it` block | PASS | Each `it` has a single logical assertion |
| AsgardDescribeSpec base | PASS | Test extends AsgardDescribeSpec |
| `lastActivityTimestamp` change is safe | PASS | All callers updated; SpawnedAgentHandle uses separate `@Volatile var Instant` field |

## Documentation Updates Needed

None. The implementation aligns with the spec. The `SIGNAL_ACTION` ValType being unused is minor.

## Test Results

- `./test.sh`: PASS (exit code 0)
- `./sanity_check.sh`: PASS (exit code 0)
- All 1035+ tests pass including 21 new ShepherdServer tests
