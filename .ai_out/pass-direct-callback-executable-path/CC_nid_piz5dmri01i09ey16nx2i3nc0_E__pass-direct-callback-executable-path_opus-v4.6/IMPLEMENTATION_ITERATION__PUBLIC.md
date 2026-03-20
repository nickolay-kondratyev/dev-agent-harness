# Implementation Iteration: Fix Missed Callback Script Path Spots

## Summary

Fixed two critical missed spots where bare `callback_shepherd.signal.sh` script names were still used instead of full absolute paths from `CallbackScriptsDir`.

## Changes

### Issue 1: `AckedPayloadSender.wrapPayload()` — Fixed

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt`

- Added `callbackScriptsDir: CallbackScriptsDir` to `AckedPayloadSenderImpl` constructor
- Changed `wrapPayload()` static method to accept `callbackSignalScriptPath: String` parameter instead of reading from `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT`
- The `MUST_ACK_BEFORE_PROCEEDING` attribute in payload XML now uses the full absolute path

### Issue 2: `SubPartConfigBuilder.BOOTSTRAP_MESSAGE` — Fixed

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilder.kt`

- Added `callbackScriptsDir: CallbackScriptsDir` to `SubPartConfigBuilder` constructor
- Changed `BOOTSTRAP_MESSAGE` from `companion object const` to a `private fun bootstrapMessage()` that uses `callbackScriptsDir.signalScriptPath`
- The bootstrap message (first instruction the agent receives) now uses the full absolute path

### Call Sites Updated

**Production code**:
- `PartExecutorInfraBuilder.buildAgentFacade()` — passes `shepherdContext.infra.callbackScriptsDir` to `AckedPayloadSenderImpl`
- `ProductionPartExecutorFactoryCreator.create()` — passes `callbackScriptsDir` to `SubPartConfigBuilder`
- `ProductionPlanningPartExecutorFactory.create()` — passes `callbackScriptsDir` to `SubPartConfigBuilder`

**Test code**:
- `AckedPayloadSenderTest` — all 6 `AckedPayloadSenderImpl` constructions updated with `ContextTestFixtures.TEST_CALLBACK_SCRIPTS_DIR`; `wrapPayload` calls now pass the script path
- `SubPartConfigBuilderTest` — `createBuilder()` passes `ContextTestFixtures.TEST_CALLBACK_SCRIPTS_DIR`; bootstrap message assertion updated to expect full path
- `PartExecutorFactoryCreatorTest` — `testConfigBuilder()` passes `CallbackScriptsDir.unvalidated()`
- `ProductionPlanningPartExecutorFactoryTest` — `SubPartConfigBuilder` construction passes `CallbackScriptsDir.unvalidated()`
- `AgentFacadeImplIntegTest` — `AckedPayloadSenderImpl` construction passes `scriptsDir`
- `SelfCompactionIntegTest` — `FacadeDeps` data class extended with `callbackScriptsDir` field

## Test Results

All 1946 tests pass, 0 failures. No tests removed or skipped.

## Skipped Items (as instructed)

- DRY violations in test files (hardcoded paths vs `ContextTestFixtures`) — follow-up ticket scope
- Duplicated `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` / `CallbackScriptsDir.REQUIRED_SCRIPT` constants — follow-up ticket scope
