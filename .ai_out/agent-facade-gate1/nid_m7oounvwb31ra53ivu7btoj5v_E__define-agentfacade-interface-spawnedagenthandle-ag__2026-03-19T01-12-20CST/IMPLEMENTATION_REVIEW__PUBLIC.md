# Gate 1 Review: AgentFacade Interface Definitions

## Verdict: PASS (with 1 IMPORTANT issue to address before Gate 2)

---

## Summary

Gate 1 introduces 6 files in `com.glassthought.shepherd.core.agent.facade`:
- `AgentFacade.kt` — interface with 4 suspend methods
- `AgentSignal.kt` — sealed class (4 variants) + `DoneResult` enum
- `SpawnedAgentHandle.kt` — data class (guid, sessionId, lastActivityTimestamp)
- `SpawnAgentConfig.kt` — data class (8 fields)
- `AgentPayload.kt` — data class (instructionFilePath)
- `ContextWindowState.kt` — data class (remainingPercentage: Int?)

All files compile. Tests pass (`./gradlew :app:test` exit 0). Sanity check passes. No duplicate `ContextWindowState` exists elsewhere. Existing types (`HandshakeGuid`, `ResumableAgentSessionId`, `AgentType`) are correctly reused — not duplicated. All files have anchor points and KDoc.

Overall: well-aligned with spec, clean types, good documentation. One structural issue with `SpawnedAgentHandle` warrants attention.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `SpawnedAgentHandle` is a `data class` with a mutable `@Volatile var` field — problematic semantics

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnedAgentHandle.kt`

**What:** `SpawnedAgentHandle` is declared as `data class` with `@Volatile var lastActivityTimestamp: Instant`. Kotlin data classes auto-generate `equals()`, `hashCode()`, `copy()`, and `toString()` that include ALL constructor properties — including this mutable one.

**Why this matters:**
1. **`equals`/`hashCode` instability**: Two references to the same handle will compare as not-equal if `lastActivityTimestamp` was updated between comparisons. If handles are ever used as map keys or in sets (e.g., tracking active handles), this creates subtle bugs.
2. **`copy()` creates a snapshot**: `handle.copy()` captures the current timestamp, creating a stale detached copy that diverges silently from the real handle. The `@Volatile` annotation is meaningless on the copy.
3. **Conceptual mismatch**: A `data class` communicates "value type with structural equality." A handle with a mutating field is an identity-based entity, not a value.

**Suggested fix — choose one:**

**Option A (recommended):** Make it a regular `class` with `guid` and `sessionId` determining identity:

```kotlin
class SpawnedAgentHandle(
    val guid: HandshakeGuid,
    val sessionId: ResumableAgentSessionId,
    @Volatile var lastActivityTimestamp: Instant,
) {
    override fun equals(other: Any?): Boolean =
        other is SpawnedAgentHandle && guid == other.guid

    override fun hashCode(): Int = guid.hashCode()

    override fun toString(): String =
        "SpawnedAgentHandle(guid=$guid, sessionId=$sessionId, lastActivityTimestamp=$lastActivityTimestamp)"
}
```

**Option B:** Keep `data class` but exclude the mutable field from the constructor (put it in the body). This prevents it from participating in `equals`/`hashCode`/`copy` but makes it less visible:

```kotlin
data class SpawnedAgentHandle(
    val guid: HandshakeGuid,
    val sessionId: ResumableAgentSessionId,
) {
    @Volatile var lastActivityTimestamp: Instant = Instant.EPOCH
}
```

Option A is preferred because the handle is fundamentally an identity-based entity.

**Severity:** IMPORTANT — must resolve before Gate 2 where `FakeAgentFacade` will manipulate handles and potentially compare them.

---

## Suggestions

### 1. `SelfCompacted` should be `data object` instead of `object`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt`

**What:** `SelfCompacted` is declared as `object SelfCompacted : AgentSignal()`. Kotlin 1.9+ supports `data object` which provides a cleaner `toString()` (prints `SelfCompacted` instead of `AgentSignal$SelfCompacted@hash`) and consistent behavior with the other `data class` variants.

**Suggested fix:**
```kotlin
data object SelfCompacted : AgentSignal()
```

Minor improvement for log readability.

### 2. `AgentSpawnException` referenced in KDoc but does not exist

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt` (line 33)

**What:** The `@throws AgentSpawnException` in `spawnAgent` KDoc references a class that does not exist yet. This is acceptable for Gate 1 (interfaces only, no implementations), but should be created as part of Gate 2/4 when `AgentFacadeImpl` is implemented. It should extend `AsgardBaseException` per CLAUDE.md standards.

**No action needed now** — just flagging for Gate 2 tracking.

### 3. Consider whether `ContextWindowState` needs a `data object` variant for "unknown"

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/ContextWindowState.kt`

**What:** Currently `null` in `remainingPercentage` signals "stale/unknown." This is documented well. An alternative would be a sealed class with `Known(val percentage: Int)` and `Unknown` variants — making the semantics compile-time enforced. However, the current approach is simpler and the KDoc is clear about `null` semantics. This is a style preference, not a defect. **No action recommended** — the current design is fine for V1.

---

## Spec Alignment Check

| Spec Requirement | Status |
|---|---|
| 4 methods on AgentFacade (spawnAgent, sendPayloadAndAwaitSignal, readContextWindowState, killSession) | PASS |
| All methods are `suspend` | PASS |
| AgentSignal: 4 variants (Done, FailWorkflow, Crashed, SelfCompacted) | PASS |
| DoneResult enum: COMPLETED, PASS, NEEDS_ITERATION | PASS |
| SpawnedAgentHandle: guid (HandshakeGuid), sessionId (ResumableAgentSessionId), lastActivityTimestamp (Instant) | PASS |
| SpawnAgentConfig: all 8 fields from ticket | PASS |
| AgentPayload: instructionFilePath (Path) | PASS |
| ContextWindowState: remainingPercentage (Int?) | PASS |
| Q&A handling documented as internal to sendPayloadAndAwaitSignal | PASS — KDoc on line 50-54 |
| No implementations — interfaces and data classes only | PASS |
| Package: `com.glassthought.shepherd.core.agent.facade` | PASS |
| Anchor points on all types | PASS |
| KDoc on all interface methods | PASS |
| No duplicate ContextWindowState | PASS — verified via codebase search |
| Reuses existing HandshakeGuid, ResumableAgentSessionId, AgentType | PASS |
| Code compiles | PASS — `./gradlew :app:build` exit 0 |
| Tests pass | PASS — `./gradlew :app:test` exit 0 |
| Sanity check passes | PASS — `./sanity_check.sh` exit 0 |

---

## Documentation Updates Needed

None for Gate 1. The spec notes that `SessionsState.md` and `PartExecutor.md` should be updated — those are correctly deferred to later gates per the gate plan.
