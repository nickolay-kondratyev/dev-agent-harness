# Health Monitoring Context Exploration

**Date**: 2026-03-19  
**Ticket**: nid_sfgmp7jk8tlnnh2sgblh3npkj_E  
**Task**: Implement `DetectionContext` enum + `AgentUnresponsiveUseCase`

---

## Overview

This document provides a comprehensive exploration of the health monitoring architecture needed to implement `DetectionContext` enum and `AgentUnresponsiveUseCase`. The implementation consolidates three previously separate unresponsive-agent use cases into a single parameterized class.

---

## 1. Full Specification Reference

**File**: `/app/../doc/use-case/HealthMonitoring.md` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E)

### Key Sections
- **Logging Principle** (lines 16-28): All health monitoring decisions MUST be logged with structured values (CallbackAge, SessionName, StaleDuration, etc.)
- **Flow** (lines 31-48): Complete health monitoring sequence from startup timeout through ping mechanism
- **Monitoring Loop** (lines 42-141): AgentFacadeImpl-owned health-aware await loop, NOT a separate background component
- **UseCase Classes** (lines 157-185): Three use cases: AgentUnresponsiveUseCase, FailedToExecutePlanUseCase, FailedToConvergeUseCase
- **AgentUnresponsiveUseCase — DetectionContext** (lines 165-177): The consolidation spec defining the three detection contexts

### Three Detection Contexts (DetectionContext Enum)

| Context | Trigger | Log Context | Action |
|---------|---------|-------------|--------|
| **STARTUP_TIMEOUT** | No callback within `healthTimeouts.startup` (3 min) after agent spawn | Session name, HandshakeGuid, env vars, timeout duration | Kill TMUX session. Return `PartResult.AgentCrashed` |
| **NO_ACTIVITY_TIMEOUT** | `lastActivityTimestamp` stale beyond `healthTimeouts.normalActivity` (30 min) | Session name, stale duration, normalActivity value | Ping agent via TMUX send-keys. If ping also times out → PING_TIMEOUT |
| **PING_TIMEOUT** | No callback (lastActivityTimestamp unchanged) after `healthTimeouts.pingResponse` (3 min) window | Session name, ping response duration | Mark as CRASHED, kill TMUX session, complete deferred with `AgentSignal.Crashed` |

**Design Rationale**: The three contexts share the same conceptual event (agent is unresponsive) and same outcome (kill session, return crashed). Single class with parameterized logging eliminates divergence risk and makes adding new detection triggers trivial (just add an enum value).

---

## 2. Existing Health Monitoring Code

### Current Implementation Files

1. **`app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/`**
   - `FailedToExecutePlanUseCase.kt`: Handles blocking plan-execution failures (1 interface + 1 impl)
   - `FailedToConvergeUseCase.kt`: User iteration budget prompt (1 interface + 1 impl)
   - `AllSessionsKiller.kt`: Interface for killing all TMUX sessions
   - `TicketFailureLearningUseCase.kt`: Records failure context (separate file, not explored here)

2. **Missing**: `AgentUnresponsiveUseCase.kt` (to be created)

### Existing Test Files

1. **`app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt`**
   - Pattern: Kotest DescribeSpec with BDD structure (GIVEN/WHEN/THEN)
   - Base class: `AsgardDescribeSpec` with `AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true)`
   - Test fakes: `FakeProcessExiter`, `FakeConsoleOutput`, `FakeAllSessionsKiller`, `SpyTicketFailureLearningUseCase`
   - Pattern includes `OrderTracker` for verifying execution order
   - Log checking via `logCheckOverrideAllow(LogLevel.WARN)` extension

2. **`app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToConvergeUseCaseImplTest.kt`**
   - Pattern: Kotest DescribeSpec with BDD structure
   - Base class: `AsgardDescribeSpec`
   - Test fake: `FakeUserInputReader`
   - Tests input parsing (y/Y/N/""/null, trimming, case-insensitivity)

### Test Base Class Pattern

**`AsgardDescribeSpec`** (from asgardTestTools):
```kotlin
class MyTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {
        describe("GIVEN ...") {
            it("THEN ...") {
                // assertion
            }
        }
    }
)
```

**Inherited `outFactory`**: Available automatically in test body via superclass

---

## 3. TmuxSessionManager Implementation

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt`

```kotlin
suspend fun killSession(session: TmuxSession) {
    out.info(
        "killing_tmux_session",
        Val(session.name.sessionName, ValType.STRING_USER_AGNOSTIC),
    )
    
    commandRunner.run("kill-session", "-t", session.name.sessionName)
        .orThrow("kill tmux session [${session.name.sessionName}]")
    
    out.info(
        "tmux_session_killed",
        Val(session.name.sessionName, ValType.STRING_USER_AGNOSTIC),
    )
}
```

**Key Points**:
- Logs before and after kill operation
- Uses `Out.info()` with structured `Val()` values (not string interpolation)
- Uses `ValType.STRING_USER_AGNOSTIC` for session names
- Returns through error `.orThrow()` on failure
- Parameter: `TmuxSession` object with `.name.sessionName` field

---

## 4. AgentSignal Sealed Class

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt`

```kotlin
sealed class AgentSignal {
    data class Done(val result: DoneResult) : AgentSignal()
    data class FailWorkflow(val reason: String) : AgentSignal()
    data class Crashed(val details: String) : AgentSignal()
    data object SelfCompacted : AgentSignal()
}
```

**For AgentUnresponsiveUseCase**:
- Returns `AgentSignal.Crashed(details)` where `details` is the diagnostic string
- Contains session name, detection context, last activity age, etc.
- Completed by `AgentFacadeImpl` inside health-aware await loop

---

## 5. Health Timeout Configuration

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt`

```kotlin
data class HealthTimeoutLadder(
    val startup: Duration = 3.minutes,
    val normalActivity: Duration = 30.minutes,
    val pingResponse: Duration = 3.minutes,
)

data class HarnessTimeoutConfig(
    val healthTimeouts: HealthTimeoutLadder = HealthTimeoutLadder(),
    val healthCheckInterval: Duration = 5.minutes,
    // ... other fields
) {
    companion object {
        fun forTests(): HarnessTimeoutConfig = HarnessTimeoutConfig(
            healthTimeouts = HealthTimeoutLadder(
                startup = 1.seconds,
                normalActivity = 5.seconds,
                pingResponse = 1.seconds,
            ),
            healthCheckInterval = 1.seconds,
            // ...
        )
    }
}
```

**Injection**: Via `ShepherdContext.timeoutConfig` (available to use cases via DI)

---

## 6. AllSessionsKiller Interface

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AllSessionsKiller.kt`

```kotlin
fun interface AllSessionsKiller {
    suspend fun killAllSessions()
}
```

**Purpose**: Kills all active agent TMUX sessions (used by `FailedToExecutePlanUseCase`)

**Note for AgentUnresponsiveUseCase**: 
- `NO_ACTIVITY_TIMEOUT` context → DO NOT kill; send ping instead
- `STARTUP_TIMEOUT` and `PING_TIMEOUT` contexts → Kill the specific session (via `TmuxSessionManager.killSession()`)

---

## 7. Logging Patterns

### Pattern from TmuxSessionManager

```kotlin
out.info(
    "killing_tmux_session",
    Val(session.name.sessionName, ValType.STRING_USER_AGNOSTIC),
)
```

### Required Logging for AgentUnresponsiveUseCase (per spec lines 22-26)

**STARTUP_TIMEOUT**:
```kotlin
out.info(
    "startup_timeout_detected",
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(timeoutDuration, TIMEOUT_DURATION_VALTYPE), // need to define
)
```

**NO_ACTIVITY_TIMEOUT**:
```kotlin
out.info(
    "no_activity_timeout_detected",
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(staleDuration, STALE_DURATION_VALTYPE), // need to define
)
```

**PING_TIMEOUT**:
```kotlin
out.info(
    "ping_timeout_detected",
    Val(sessionName, ValType.STRING_USER_AGNOSTIC),
    Val(pingResponseDuration, PING_RESPONSE_DURATION_VALTYPE), // need to define
)
```

### ValType Usage

**From existing code** (TmuxSessionManager, InstructionSection):
- `ValType.STRING_USER_AGNOSTIC` — for session names, paths, shell commands

**Expected new ValTypes** (need to define):
- `TIMEOUT_DURATION` — for Duration values in timeout contexts
- `STALE_DURATION` — for elapsed time since last activity
- `PING_RESPONSE_DURATION` — for ping response window durations
- `TMUX_SESSION_NAME` — semantic specialization if needed

**Note**: These may already exist in asgardCore's ValType enum or may need to be defined in the codebase. Grep showed no existing project-specific ValType enum, suggesting asgardCore's ValType is used directly.

---

## 8. FakeAgentFacade Test Pattern

**File**: `/app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt`

```kotlin
class FakeAgentFacade : AgentFacade {
    private val _spawnCalls = mutableListOf<SpawnAgentConfig>()
    private val _sendPayloadCalls = mutableListOf<SendPayloadCall>()
    
    val spawnCalls: List<SpawnAgentConfig> get() = _spawnCalls.toList()
    val sendPayloadCalls: List<SendPayloadCall> get() = _sendPayloadCalls.toList()
    
    private var spawnHandler: suspend (SpawnAgentConfig) -> SpawnedAgentHandle = {
        error("FakeAgentFacade: spawnAgent not programmed. Call onSpawn { ... } first.")
    }
    
    fun onSpawn(handler: suspend (SpawnAgentConfig) -> SpawnedAgentHandle) {
        spawnHandler = handler
    }
    
    override suspend fun spawnAgent(config: SpawnAgentConfig): SpawnedAgentHandle {
        _spawnCalls.add(config)
        return spawnHandler(config)
    }
}
```

**Pattern**:
- Recorded interactions (lists of calls)
- Programmable handlers (lambdas with defaults)
- Handlers default to `error()` for fail-hard semantics
- Exception: `killSession` defaults to no-op since kill is typically not the behavior under test
- Call recording enables interaction verification

**Implication for AgentUnresponsiveUseCase testing**:
- Mock `TmuxSessionManager.killSession()` behavior
- Mock `AckedPayloadSender` for sending pings (NO_ACTIVITY_TIMEOUT path)
- Record which detection context triggered
- Verify correct session was killed
- Verify correct `Crashed` signal was returned with appropriate details

---

## 9. Dependency Ticket: nid_0of6zl2493ctvmy9m23kxnhnl_E

**File**: `_tickets/implement-healthtimeoutladder-refactor-harnesstimeoutconfig.md`

**Status**: CLOSED (2026-03-19)

**What It Covered**:
- Implemented `HealthTimeoutLadder` data class
- Refactored `HarnessTimeoutConfig` to use `.healthTimeouts: HealthTimeoutLadder`
- Updated `forTests()` with fast timeout values
- This is a DEPENDENCY for AgentUnresponsiveUseCase implementation

**Implication**: The timeout configuration is already set up and ready to use in AgentUnresponsiveUseCase.

---

## 10. AgentFacade Interface (for context)

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt`

Key method for AgentUnresponsiveUseCase interaction:

```kotlin
suspend fun sendPayloadAndAwaitSignal(
    handle: SpawnedAgentHandle,
    payload: AgentPayload
): AgentSignal
```

**Called by**: PartExecutor  
**Returns**: AgentSignal (Done, FailWorkflow, Crashed, SelfCompacted)  
**Internal behavior**: Runs health-aware await loop with health pings

The health monitoring logic is encapsulated INSIDE this method, not external. This is where the AgentUnresponsiveUseCase interacts with the health loop.

---

## 11. Key Architectural Constraints

### From Health Monitoring Spec (HealthMonitoring.md lines 42-60)

**AgentFacadeImpl owns the health-aware await loop** — it is:
- NOT a separate background component
- NOT a separate coroutine competing to complete the deferred
- Scoped naturally to the facade's await lifetime
- Single control flow with the facade creating and registering the deferred

### Implications for AgentUnresponsiveUseCase

1. **Not a long-lived service**: AgentUnresponsiveUseCase is a simple, stateless use case invoked by AgentFacadeImpl
2. **No async spawning**: The use case receives a fully-constructed request and returns a result synchronously
3. **Error handling**: Exceptions (TMUX failure, logging failure) bubble up; no try/catch in the use case itself
4. **Timing**: All timing decisions (should we ping? should we kill?) are made by AgentFacadeImpl's loop

---

## 12. Related Files for Reference

| File | Purpose | Relevance |
|------|---------|-----------|
| `/doc/high-level.md` | Entry point for all specs | Overall context |
| `doc/core/agent-to-server-communication-protocol.md` | Agent callback protocol | AckedPayloadSender, ping mechanism |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnedAgentHandle.kt` | Handle passed to use cases | Contains sessionGuid, lastActivityTimestamp |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/ContextWindowState.kt` | Context window reading (not used for liveness) | For reference only |
| `app/src/main/kotlin/com/glassthought/shepherd/core/infra/` | Infrastructure services | ConsoleOutput, UserInputReader, ProcessExiter patterns |

---

## 13. Implementation Checklist

### DetectionContext Enum

```kotlin
enum class DetectionContext {
    STARTUP_TIMEOUT,
    NO_ACTIVITY_TIMEOUT,
    PING_TIMEOUT
}
```

### AgentUnresponsiveUseCase Interface & Implementation

**Location**: `/app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt`

**Interface**:
```kotlin
fun interface AgentUnresponsiveUseCase {
    suspend fun handleUnresponsiveAgent(
        detectionContext: DetectionContext,
        handle: SpawnedAgentHandle,
        // + other context needed: session name, timeout duration, stale duration, etc.
    ): AgentSignal.Crashed
}
```

**Implementation** (`AgentUnresponsiveUseCaseImpl`):
1. Log the detection reason with structured values (per spec lines 22-26)
2. If context == NO_ACTIVITY_TIMEOUT:
   - Send health ping via `AckedPayloadSender` (NOT kill)
   - Return without crashing signal (let facade continue loop)
3. If context == STARTUP_TIMEOUT or PING_TIMEOUT:
   - Kill the TMUX session via `TmuxSessionManager.killSession()`
   - Return `AgentSignal.Crashed(details)` with diagnostic string
4. All operations must be suspend (called from coroutine context)
5. Use constructor injection for dependencies: `OutFactory`, `TmuxSessionManager`, `AckedPayloadSender`

### Test Coverage

**Location**: `/app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCaseImplTest.kt`

**Base class**: `AsgardDescribeSpec`

**Test scenarios** (one assert per test):
1. STARTUP_TIMEOUT context → kills session
2. STARTUP_TIMEOUT context → returns Crashed signal
3. STARTUP_TIMEOUT context → logs with session name
4. NO_ACTIVITY_TIMEOUT context → sends ping (NOT kill)
5. NO_ACTIVITY_TIMEOUT context → logs with stale duration
6. PING_TIMEOUT context → kills session
7. PING_TIMEOUT context → returns Crashed signal
8. PING_TIMEOUT context → logs with timeout duration
9. TmuxSessionManager.killSession failure → exception bubbles up
10. Logging with structured values (OutFactory call verification)

---

## 14. Integration Points

### 1. AgentFacadeImpl (health-aware await loop)
- Calls `AgentUnresponsiveUseCase.handleUnresponsiveAgent()` when timeout detected
- Receives `AgentSignal.Crashed` for STARTUP_TIMEOUT and PING_TIMEOUT
- Receives nothing for NO_ACTIVITY_TIMEOUT (ping is fire-and-forget, loop continues)

### 2. PartExecutor
- Maps `AgentSignal.Crashed` to `PartResult.AgentCrashed`
- Delegates to `FailedToExecutePlanUseCase` which prints error, kills sessions, exits

### 3. Out/OutFactory Logging
- All health monitoring decisions logged with `out.info()`, `out.warn()`, etc.
- Structured values via `Val(value, ValType.XXX)`
- No string interpolation in log messages

---

## 15. Testing Strategy Hints

**From HealthMonitoring.md lines 142-150**:
- Health monitoring is unit-tested via `FakeAgentFacade` + virtual time
- `FakeAgentFacade` controls `lastActivityTimestamp` advancement
- `TestClock` (ref.ap.whDS8M5aD2iggmIjDIgV9.E) controls `now()` for timestamp age comparisons
- `advanceTimeBy()` controls coroutine delays
- Tests verify every decision branch: standard ping, crash declaration, proof-of-life acceptance

**For AgentUnresponsiveUseCase unit tests**:
- Mock `TmuxSessionManager` to verify `killSession()` is called with correct session
- Mock `AckedPayloadSender` to verify ping is sent (or NOT sent for PING_TIMEOUT/STARTUP_TIMEOUT)
- Verify `out.info()` calls with correct `DetectionContext` value
- Use `FakeAgentFacade` in orchestration-level integration tests

---

## 16. Summary

The health monitoring system is a well-designed, spec-driven architecture where:

1. **Consolidation**: Three unresponsive-agent scenarios (startup, no-activity, ping-timeout) are unified in `DetectionContext` enum + single `AgentUnresponsiveUseCase` class

2. **Ownership**: AgentFacadeImpl owns the health-aware await loop — no separate background component

3. **Logging**: Every decision point logs structured values (session name, timeouts, durations) for full audit trail

4. **Simplicity**: Use case is stateless and synchronous (suspend but no async spawning)

5. **Testing**: Comprehensive unit test coverage via fakes and virtual time enables fast, deterministic tests

6. **Extensibility**: Adding new detection triggers is trivial — just add an enum value and a new case in the use case

Ready for implementation!

