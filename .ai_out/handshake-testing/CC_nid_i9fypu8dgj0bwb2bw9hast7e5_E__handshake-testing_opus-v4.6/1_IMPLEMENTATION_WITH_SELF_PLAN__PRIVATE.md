# Implementation Plan: Fix Handshake Startup

## Task Understanding
The agent spawned in tmux cannot complete handshake because:
1. `TICKET_SHEPHERD_SERVER_PORT` env var is not exported in the tmux command
2. `callback_shepherd.signal.sh` is not on PATH in the tmux session
3. Bootstrap message doesn't instruct the agent to call `callback_shepherd.signal.sh started`

## Plan

**Goal**: Make ClaudeCodeAdapter export server port and callback scripts PATH, and update bootstrap message.

**Steps**:
1. [x] Add `serverPort: Int` and `callbackScriptsDir: String` to `ClaudeCodeAdapter` constructor
2. [x] Export both in `buildStartCommand()` inner command
3. [x] Update `create()` factory to accept and pass them through
4. [x] Update `ContextInitializerImpl` to read port from env and resolve scripts dir, pass to adapter
5. [x] Update `SubPartConfigBuilder.BOOTSTRAP_MESSAGE` to instruct agent to call `started`
6. [x] Update `ClaudeCodeAdapterTest` for new constructor params and verify exports
7. [x] Update test helpers (`ShepherdInitializerTest`, `TicketShepherdCreatorTest`) that construct `ClaudeCodeAdapter.create()`
8. [x] Run `:app:test` and verify all tests pass

**Files touched**:
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` (ContextInitializerImpl)
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilder.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapterTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializerTest.kt`

## Status: COMPLETE
All steps done. All tests passing.
