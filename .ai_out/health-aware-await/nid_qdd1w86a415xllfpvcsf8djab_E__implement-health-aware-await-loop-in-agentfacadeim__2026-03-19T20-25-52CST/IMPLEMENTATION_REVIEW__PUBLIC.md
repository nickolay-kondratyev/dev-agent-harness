# Implementation Review: Health-Aware Await Loop in AgentFacadeImpl

## Summary

Replaced the V1 stub `sendPayloadAndAwaitSignal` (raw `send-keys` + bare `deferred.await()`) with a full health-aware await loop. The implementation adds ACK-wrapped payload delivery, health monitoring with ping/crash detection, and Q&A suppression. Three new constructor dependencies were added: `AckedPayloadSender`, `AgentUnresponsiveUseCase`, `QaDrainAndDeliverUseCase`.

**Overall Assessment**: Solid implementation that closely follows the spec. The `HealthAwareAwaitLoop` inner class is well-structured with clear separation of concerns. Tests cover all major branches. Two important issues to address below.

**Tests pass**: `./test.sh` exits 0, `./sanity_check.sh` exits 0.

**No tests removed**: The two `it` blocks removed from the old test file were replaced with equivalent or expanded versions in the new test structure.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `checkStaleness()` ignores the return value of `AgentUnresponsiveUseCase.handle()`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt` lines 401-410

```kotlin
agentUnresponsiveUseCase.handle(
    detectionContext = DetectionContext.NO_ACTIVITY_TIMEOUT,
    tmuxSession = tmuxSession,
    diagnostics = UnresponsiveDiagnostics(
        handshakeGuid = handle.guid,
        timeoutDuration = normalActivity,
        staleDuration = callbackAge,
    ),
)
return StalenessAction.PING_SENT
```

The return value of `handle()` is discarded. While the current `AgentUnresponsiveUseCaseImpl` returns `PingSent` for `NO_ACTIVITY_TIMEOUT`, the contract of `UnresponsiveHandleResult` allows `SessionKilled` to be returned for any context. If the implementation ever changes, this code would silently proceed to the ping-wait phase after the session was already killed.

**Suggested fix**: Handle the result explicitly:
```kotlin
val result = agentUnresponsiveUseCase.handle(...)
return when (result) {
    is UnresponsiveHandleResult.PingSent -> StalenessAction.PING_SENT
    is UnresponsiveHandleResult.SessionKilled -> {
        signalDeferred.complete(result.signal)
        sessionsState.remove(handle.guid)
        // Need a new StalenessAction variant or refactor to return PingOutcome directly
    }
}
```

Alternatively, if the spec **guarantees** `PingSent` for `NO_ACTIVITY_TIMEOUT`, document this assumption with a check:
```kotlin
val result = agentUnresponsiveUseCase.handle(...)
check(result is UnresponsiveHandleResult.PingSent) {
    "Expected PingSent for NO_ACTIVITY_TIMEOUT but got $result"
}
return StalenessAction.PING_SENT
```

### 2. `QaDrainAndDeliverUseCase` made `open` instead of extracting an interface -- violates DIP

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCase.kt` lines 22, 34

Making a class `open` for testability is an anti-pattern in this codebase where DIP (Dependency Inversion Principle) is a core standard and interfaces are explicitly favored. The CLAUDE.md says "DIP - Dependency Inversion Principle - Or in other words We Like Interfaces :)" and "Be classy and use interfaces."

The test (`QaDrainTracker`) already creates a subclass override, which works but:
- Makes the class hierarchy fragile (callers don't know which behavior they get)
- Breaks the pattern used by every other dependency in `AgentFacadeImpl` (all others are interfaces)

**Suggested fix**: Extract a `fun interface QaDrainer` (or similar) with a single `drainAndDeliver` method, have `QaDrainAndDeliverUseCase` implement it, and inject the interface into `AgentFacadeImpl`. Remove `open` from the class and method.

---

## Suggestions

### 1. `timestampBeforePing` is captured AFTER the ping was already sent

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt` line 418

In `awaitPingProofOfLife()`, the code captures `timestampBeforePing` at the start of the method. But this method is called AFTER `checkStaleness()` already sent the ping (via `agentUnresponsiveUseCase.handle(NO_ACTIVITY_TIMEOUT)`). There's a small window where a callback could arrive between the ping being sent and `timestampBeforePing` being captured, causing the proof-of-life check to use a timestamp that already reflects the ping's ACK.

In practice this is not a correctness issue -- it would only cause a false "alive" which is conservative and safe. But capturing the timestamp before calling `agentUnresponsiveUseCase.handle()` in `checkStaleness()` and passing it to `awaitPingProofOfLife()` would be more precise.

### 2. Consider a `check(callbackAge >= Duration.ZERO)` guard in `checkStaleness()`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-5/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt` line 381-382

```kotlin
val ageMs = java.time.Duration.between(lastActivity, clock.now()).toMillis()
val callbackAge = ageMs.milliseconds
```

If `lastActivityTimestamp` is somehow in the future relative to `clock.now()` (e.g., clock skew or a bug), `ageMs` would be negative. The code handles this correctly (negative < normalActivity = FRESH), but a defensive log at WARN level for negative ages would help catch bugs in timestamp management.

### 3. Test file has some duplication in health-aware loop test setup

Many of the `runTest` blocks in the `sendPayloadAndAwaitSignal` describe group repeat the same pattern:
1. Create harness
2. Spawn agent
3. Create payload
4. Launch coroutine
5. advanceTimeBy

Consider extracting a helper that returns the harness + handle + entry + resultDeferred for health-loop tests. This would reduce boilerplate and make each test body focus on the specific scenario.

---

## Documentation Updates Needed

None -- the implementation doc and spec are already aligned.
