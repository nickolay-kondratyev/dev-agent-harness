# Implementation Review: DetectionContext enum + AgentUnresponsiveUseCase

**Date**: 2026-03-19
**Verdict**: **PASS** (with IMPORTANT items that should be addressed)

---

## Summary

The implementation adds three new files:
- `DetectionContext.kt` — enum with 3 values (`STARTUP_TIMEOUT`, `NO_ACTIVITY_TIMEOUT`, `PING_TIMEOUT`)
- `AgentUnresponsiveUseCase.kt` — interface, impl, sealed result type, diagnostics data class, `SingleSessionKiller` fun interface
- `AgentUnresponsiveUseCaseImplTest.kt` — 14 BDD-style tests covering all 3 contexts, exception bubbling, and diagnostic values

Overall this is a clean, well-structured implementation that follows the spec and project conventions. No existing tests or functionality were removed. Tests pass (`./gradlew :app:test` exits 0).

---

## No CRITICAL Issues

No security, correctness, or data loss issues found.

---

## IMPORTANT Issues

### 1. `Pair` usage in test fakes violates CLAUDE.md coding standards

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCaseImplTest.kt`
**Lines**: 35-36

```kotlin
internal class SpyTmuxCommunicator : TmuxCommunicator {
    val sentKeys = mutableListOf<Pair<String, String>>()
    val sentRawKeys = mutableListOf<Pair<String, String>>()
```

CLAUDE.md is explicit: "No `Pair`/`Triple` -- create descriptive `data class`." While this is in test code and DRY matters less there, the standard applies broadly. A simple `data class SendKeysCall(val paneTarget: String, val keys: String)` would be more self-documenting and consistent with the project's `SpawnCall`, `SendPayloadCall` patterns seen in `FakeAgentFacade`.

### 2. `NO_ACTIVITY_TIMEOUT` and `PING_TIMEOUT` logging branches are identical -- DRY violation

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt`
**Lines**: 155-167

```kotlin
DetectionContext.NO_ACTIVITY_TIMEOUT -> out.info(
    "no_activity_timeout_detected",
    Val(tmuxSession.name.sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(diagnostics.staleDuration.toString(), ValType.STRING_USER_AGNOSTIC),
    Val(diagnostics.timeoutDuration.toString(), ValType.STRING_USER_AGNOSTIC),
)

DetectionContext.PING_TIMEOUT -> out.info(
    "ping_timeout_detected",
    Val(tmuxSession.name.sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(diagnostics.staleDuration.toString(), ValType.STRING_USER_AGNOSTIC),
    Val(diagnostics.timeoutDuration.toString(), ValType.STRING_USER_AGNOSTIC),
)
```

The only difference is the log message string. The structured values are identical. This is borderline -- the two contexts ARE conceptually different events, and the different message strings serve observability. However, if a fourth context is added with the same pattern, this should be refactored to pass the message string as a parameter. Acceptable as-is given there are only 3 branches, but worth flagging.

**Counter-argument to DRY here**: The `when` exhaustiveness guarantee (no `else`) is more valuable than deduplicating these two branches. Keeping them separate means the compiler forces you to handle each new enum value explicitly. This is a reasonable tradeoff.

### 3. `AgentUnresponsiveUseCase` is NOT a `fun interface` unlike peer `FailedToExecutePlanUseCase`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt`
**Line**: 45

```kotlin
interface AgentUnresponsiveUseCase {
```

The peer use case `FailedToExecutePlanUseCase` is a `fun interface`. `AgentUnresponsiveUseCase` has a single method (`handle`), so it qualifies for `fun interface`. This is a consistency question -- either both should be `fun interface` or neither. Since the project convention (per `AllSessionsKiller`, `SingleSessionKiller`, `FailedToExecutePlanUseCase`) favors `fun interface` for single-method interfaces, this should match.

---

## Suggestions

### 1. Consider semantically specific `ValType` values instead of `STRING_USER_AGNOSTIC` everywhere

All structured log values use `ValType.STRING_USER_AGNOSTIC`, including durations (lines 152, 158-159, 164-165). The CLAUDE.md states: "`ValType` must be **semantically specific** to the value being logged." Duration values could benefit from a more specific type if one exists in the `ValType` enum. However, looking at the existing codebase patterns (e.g., `ClaudeCodeAdapter`, `TmuxCommunicatorImpl`), `STRING_USER_AGNOSTIC` is used broadly for non-PII values. This appears to be the project convention. No action needed unless a `DURATION` ValType is added later.

### 2. Test setup duplication

Many test cases repeat the same 3-line setup:
```kotlin
val fakeKiller = FakeSingleSessionKiller()
val tmuxSession = createTmuxSession()
val useCase = createUseCase(outFactory, fakeKiller)
```

This is acceptable per CLAUDE.md ("DRY... Much less important in tests and boilerplate"), and each `it` block being self-contained makes them independently readable. No action needed.

### 3. `UnresponsiveDiagnostics.staleDuration` field semantics for `STARTUP_TIMEOUT`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt`
**Lines**: 62-74

The `UnresponsiveDiagnostics` data class has `staleDuration` documented as relevant for `NO_ACTIVITY_TIMEOUT` and `PING_TIMEOUT`, but the `STARTUP_TIMEOUT` logging branch (line 148-153) does not log it. Yet callers must still provide a value. The implementation correctly ignores it for `STARTUP_TIMEOUT` context. This is fine -- the caller can pass `Duration.ZERO` or the actual elapsed time. Worth considering whether the documentation should explicitly state what to pass for irrelevant fields, or whether the class should use nullable `Duration?` for context-specific fields. However, the current approach (non-null with documented context-relevance) is simpler and avoids null-checking noise.

### 4. The `details` string in `killSessionAndReturnCrashed` uses string concatenation

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt`
**Lines**: 115-119

```kotlin
val details = "Agent unresponsive: context=${detectionContext.name}, " +
    "session=${tmuxSession.name.sessionName}, " +
    "handshakeGuid=${diagnostics.handshakeGuid}, " +
    "staleDuration=${diagnostics.staleDuration}, " +
    "timeoutDuration=${diagnostics.timeoutDuration}"
```

This is NOT a log message -- it is a diagnostic payload passed to `AgentSignal.Crashed(details)`, so string interpolation is acceptable here. The implementation PUBLIC.md correctly documents this distinction. No action needed.

---

## Spec Compliance Check

| Requirement | Status |
|---|---|
| Three detection contexts (STARTUP_TIMEOUT, NO_ACTIVITY_TIMEOUT, PING_TIMEOUT) | PASS |
| STARTUP_TIMEOUT and PING_TIMEOUT kill session | PASS |
| NO_ACTIVITY_TIMEOUT sends ping, does NOT kill | PASS |
| Structured logging for all contexts | PASS |
| Session name logged | PASS |
| HandshakeGuid logged for STARTUP_TIMEOUT | PASS |
| Timeout duration logged | PASS |
| Stale duration logged for NO_ACTIVITY and PING | PASS |
| Returns `AgentSignal.Crashed` for kill contexts | PASS |
| `when` exhaustive (no `else` branch) | PASS -- both in `handle()` and `logDetection()` |
| DIP (interface + impl, constructor injection) | PASS |
| SRP (single class, single responsibility) | PASS |
| BDD tests with one assert per `it` | PASS |
| Exception bubbling (no swallowed exceptions) | PASS |
| No existing tests removed | PASS |

---

## Architecture Assessment

The `SingleSessionKiller` fun interface is a good design choice. It follows DIP by decoupling the use case from `TmuxSessionManager` (a concrete class with infrastructure deps). At wiring time, `tmuxSessionManager::killSession` can be passed as a lambda. This is consistent with the `AllSessionsKiller` pattern already in the codebase.

The `UnresponsiveHandleResult` sealed class is a thoughtful addition not explicitly in the spec. It makes the bifurcation between "session killed" and "ping sent" type-safe, preventing the caller from accidentally treating a ping-sent result as a crash. The spec says "all contexts result in the same outcome" but then contradicts itself by specifying NO_ACTIVITY_TIMEOUT sends a ping. The sealed class correctly captures this nuance.

---

## Documentation Updates Needed

None. CLAUDE.md does not need updates for this change.
