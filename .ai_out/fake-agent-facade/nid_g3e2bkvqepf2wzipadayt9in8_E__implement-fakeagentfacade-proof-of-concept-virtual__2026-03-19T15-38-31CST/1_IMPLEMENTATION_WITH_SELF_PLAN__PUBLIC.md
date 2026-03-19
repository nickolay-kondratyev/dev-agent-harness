# PUBLIC: FakeAgentFacade Implementation

## What was implemented

### 1. FakeAgentFacade
**File**: `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt`

A programmable fake implementing `AgentFacade` for unit testing the orchestration layer:

- **Programmable handlers** via `onSpawn`, `onSendPayloadAndAwaitSignal`, `onReadContextWindowState`, `onKillSession`
- **Interaction recording** via `spawnCalls`, `sendPayloadCalls`, `readContextWindowStateCalls`, `killSessionCalls`
- **Fail-hard defaults**: unprogrammed handlers throw `IllegalStateException` (except `killSession` which defaults to no-op recording)
- **Supporting type**: `SendPayloadCall` data class captures both handle and payload for verification

### 2. FakeAgentFacadeTest (Proof-of-Concept)
**File**: `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacadeTest.kt`

BDD-style tests (AsgardDescribeSpec, one assert per `it` block) covering:

- **spawnAgent**: returns pre-programmed handle, records calls, fails hard when not programmed
- **sendPayloadAndAwaitSignal**: returns pre-programmed signals (Done, Crashed, SelfCompacted, FailWorkflow), supports sequential signal queues, records calls, fails hard when not programmed
- **readContextWindowState**: returns programmed state (with percentage or null/stale), records calls, fails hard when not programmed
- **killSession**: records calls with default no-op behavior
- **Empty state**: all call lists start empty
- **Virtual time interop**: `runTest` + `TestClock` + `advanceTimeBy` work together; demonstrates that TestClock (wall-clock axis) and coroutine virtual time (delay axis) advance independently
- **DispatcherProvider injectability**: `StandardTestDispatcher` routes coroutines through the test scheduler

## Files created
| File | Description |
|------|-------------|
| `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt` | Programmable AgentFacade fake |
| `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacadeTest.kt` | Proof-of-concept BDD tests |

## Test results
All tests pass (`./gradlew :app:test` EXIT_CODE=0), including all pre-existing tests.

## Key decisions
1. **Handlers, not queues, as the primary API**: The `onXxx { handler }` pattern is more flexible than pre-loaded queues. Tests that need sequential behavior can use `ArrayDeque` inside the handler lambda (demonstrated in the sequential signals test).
2. **killSession defaults to no-op**: Unlike other methods, `killSession` has a reasonable default (record and do nothing) since tests rarely need to program custom kill behavior.
3. **Fresh facade per `it` block**: Kotest `describe` blocks execute eagerly (not per-test), so shared mutable state (like facades with recorded calls) would leak between `it` blocks. Each `it` creates its own facade.
4. **`runCurrent()` after `advanceTimeBy`**: Required to execute pending coroutines after virtual time advancement in `runTest`.
