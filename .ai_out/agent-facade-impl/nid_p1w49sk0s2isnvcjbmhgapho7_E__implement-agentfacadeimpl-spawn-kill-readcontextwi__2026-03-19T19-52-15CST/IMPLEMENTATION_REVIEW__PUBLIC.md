# AgentFacadeImpl Implementation Review

## Summary

This PR implements `AgentFacadeImpl` -- the real `AgentFacade` implementation that wires spawn, kill, readContextWindowState, and sendPayloadAndAwaitSignal to infrastructure components. It also adds a `remove(guid)` method to `SessionsState` and bumps the detekt `constructorThreshold` from 8 to 9.

**Overall assessment: Solid implementation with good DIP usage and clear structure, but has several issues that should be addressed -- one correctness bug in `sendPayloadAndAwaitSignal`, one exception hierarchy violation, and a test that uses `delay` for synchronization contrary to project standards.**

All tests pass. `./test.sh` and `./sanity_check.sh` both exit 0.

---

## CRITICAL Issues

### C1: `sendPayloadAndAwaitSignal` sends payload via old entry's TmuxSession, not updated entry

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, lines 125-133

```kotlin
val existingEntry = sessionsState.lookup(handle.guid)
    ?: error("No SessionEntry found for guid=[${handle.guid}]")

val freshSignalDeferred = CompletableDeferred<AgentSignal>()
val updatedEntry = existingEntry.withFreshDeferred(freshSignalDeferred, clock.now())
sessionsState.register(handle.guid, updatedEntry)

// Bug: sends via existingEntry's tmuxSession, not updatedEntry's
existingEntry.tmuxAgentSession.tmuxSession.sendKeys(payload.instructionFilePath.toString())
```

The code registers `updatedEntry` in `SessionsState` but then sends the payload via `existingEntry`'s TmuxSession. Currently this works because both entries share the same `TmuxAgentSession` reference, but this is fragile and semantically wrong. If `withFreshDeferred` or `SessionEntry` construction ever copies or transforms the tmux session, this will silently break.

**Fix:** Use `updatedEntry.tmuxAgentSession.tmuxSession.sendKeys(...)` instead.

### C2: `sendPayloadAndAwaitSignal` bypasses ACK protocol -- sends raw path without ACK wrapping

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, line 133

```kotlin
existingEntry.tmuxAgentSession.tmuxSession.sendKeys(payload.instructionFilePath.toString())
```

The `AgentFacade` interface contract at line 46 explicitly states: "Delivers the payload via TMUX send-keys with ACK protocol (ref.ap.tbtBcVN2iCl1xfHJthllP.E) -- retries up to 3 times on ACK failure." The `AgentPayload` KDoc also says: "The facade wraps this path in the ACK protocol envelope and delivers it via TMUX send-keys."

The implementation sends the raw file path without ACK wrapping, without retry, and without using `AckedPayloadSender`. While the implementation doc says this is a "V1 stub", this is a contract violation that could cause agent failures in production if the agent expects the ACK protocol envelope.

**Recommendation:** Either:
1. Use `AckedPayloadSender.sendAndAwaitAck()` as designed, or
2. If this is intentionally deferred, add a clear comment explaining the gap AND document this in the public output as a known limitation, not just "V1 stub". The current comment "Send the instruction file path to the agent via TMUX send-keys" reads as if this is the intended final behavior.

---

## IMPORTANT Issues

### I1: `AgentSpawnException` extends `RuntimeException` instead of `AsgardBaseException`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, lines 293-296

```kotlin
class AgentSpawnException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

CLAUDE.md states: "Extend `AsgardBaseException` hierarchy for structured exceptions." All other exceptions in the project (`TmuxSessionCreationException`, `StartupTimeoutException`, `PlanConversionException`, `PayloadAckTimeoutException`) extend `AsgardBaseException` with structured `Val` parameters. This exception should follow the same pattern.

**Fix:** Follow the pattern in `SpawnExceptions.kt`:

```kotlin
class AgentSpawnException(
    sessionName: String,
    timeout: Duration,
    cause: Throwable? = null,
) : AsgardBaseException(
    "agent_spawn_failed",
    cause,
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(timeout.toString(), ValType.STRING_USER_AGNOSTIC),
)
```

### I2: `withFreshDeferred` discards `questionQueue` contents

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, lines 277-288

```kotlin
private fun SessionEntry.withFreshDeferred(
    signalDeferred: CompletableDeferred<AgentSignal>,
    now: java.time.Instant,
): SessionEntry = SessionEntry(
    tmuxAgentSession = tmuxAgentSession,
    partName = partName,
    subPartName = subPartName,
    subPartIndex = subPartIndex,
    signalDeferred = signalDeferred,
    lastActivityTimestamp = AtomicReference(now),
    questionQueue = ConcurrentLinkedQueue(),  // <-- new empty queue, old questions lost
)
```

When `sendPayloadAndAwaitSignal` creates a fresh entry, it creates a brand new empty `ConcurrentLinkedQueue`. If there were pending user questions in the old entry's queue, they would be silently dropped. The KDoc says "preserving all other fields" but the queue is not preserved.

For V1 where Q&A is not implemented, this is not a runtime bug. But it sets a trap for the V2 implementer who reads the KDoc and assumes questions are preserved.

**Fix:** Either pass the existing `questionQueue` reference through, or update the KDoc to explicitly note "creates a new empty questionQueue".

### I3: `withFreshDeferred` is a private file-level extension function -- violates "no free-floating functions"

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, lines 277-288

CLAUDE.md states: "Disfavor non-private free-floating functions. Favor cohesive classes; for stateless utilities, use a static class."

`withFreshDeferred` is a private file-level extension function. While it is private (not the worst case), it would be more consistent to either:
1. Move it to a private method inside `AgentFacadeImpl`, or
2. Add it as a method on `SessionEntry` itself if it belongs there.

### I4: Test uses `delay` for synchronization

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImplTest.kt`, lines 168-183

```kotlin
private suspend fun kotlinx.coroutines.CoroutineScope.spawn(
    h: Harness,
    cfg: SpawnAgentConfig = config(),
): SpawnedAgentHandle {
    val job = launch {
        while (true) {
            val removed = h.sessions.removeAllForPart(cfg.partName)
            for (entry in removed) {
                if (!entry.signalDeferred.isCompleted) {
                    entry.signalDeferred.complete(AgentSignal.Done(DoneResult.COMPLETED))
                }
            }
            kotlinx.coroutines.delay(5.milliseconds)
        }
    }
    ...
```

CLAUDE.md explicitly states: "Do NOT use `delay` for synchronization in tests. Use proper await mechanisms or polling."

Additionally, this helper is doing something destructive -- it calls `removeAllForPart` in a loop, which actually removes entries from SessionsState. This is a side effect that interferes with assertions like "THEN registers entry in SessionsState" (line 280). The test at line 280-283 passes because `registerRealEntry` runs AFTER the startup signal completes and the polling job is cancelled, but this is fragile timing.

Also, lines 401-419 use `delay(50.milliseconds)` for synchronization in the `sendPayloadAndAwaitSignal` test.

**Recommendation:** Consider using a more direct approach -- e.g., make the fake `TmuxSessionCreator` complete the deferred directly when `createSession` is called.

### I5: `System.getProperty("user.dir")` hardcoded in `createTmuxSession`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, line 173

```kotlin
workingDir = System.getProperty("user.dir"),
```

This is not injectable or testable. The `SpawnTmuxAgentSessionUseCase` takes `workingDir` as a parameter. The facade hardcodes it. This means:
1. Tests cannot verify what `workingDir` was passed without mocking `System`.
2. The working directory is not configurable per-agent.

**Recommendation:** Either add `workingDir` to `SpawnAgentConfig` or accept it as a constructor parameter on `AgentFacadeImpl`. This matches how `SpawnTmuxAgentSessionParams` handles it.

---

## Suggestions

### S1: `tools = emptyList()` is hardcoded

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, line 175

```kotlin
tools = emptyList(),
```

This means agents are always spawned with no tools restriction. If this is intentional, a comment explaining why would prevent future confusion. If tools should be configurable, add them to `SpawnAgentConfig`.

### S2: `appendSystemPrompt = true` is hardcoded

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`, line 177

Same concern as S1. This should either be configurable via `SpawnAgentConfig` or have a comment explaining why `true` is always correct.

### S3: Placeholder `TmuxAgentSession` is large and could be extracted

The `PLACEHOLDER_TMUX_AGENT_SESSION` companion object block (lines 238-270) is 30+ lines of boilerplate. Consider extracting a factory method or companion on `TmuxAgentSession` like `TmuxAgentSession.placeholder()` if this pattern is reusable.

### S4: Consider DRY for the timeout test setup

The timeout tests (lines 287-334) repeat the same `HarnessTimeoutConfig` construction three times. A private helper or constant would reduce duplication.

---

## Documentation Updates Needed

None required. The implementation public doc accurately describes what was done.
