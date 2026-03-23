# Implementation Private State

## Completed

### Pass 1 (1_IMPLEMENTATION_WITH_SELF_PLAN__PUBLIC.md)
- Threaded `CallbackScriptsDir` through `ContextForAgentProvider` and instruction sections
- All instruction sections now use full absolute paths from `CallbackScriptsDir.signalScriptPath`/`queryScriptPath`
- Created `ContextTestFixtures` with shared test constants

### Pass 2 (Iteration — this pass)
- Fixed `AckedPayloadSender.wrapPayload()`: now accepts `callbackSignalScriptPath` parameter
- Fixed `SubPartConfigBuilder.BOOTSTRAP_MESSAGE`: now a method using `callbackScriptsDir.signalScriptPath`
- Updated `AckedPayloadSenderImpl` constructor: added `callbackScriptsDir` parameter
- Updated `SubPartConfigBuilder` constructor: added `callbackScriptsDir` parameter
- Updated all production call sites (3 files)
- Updated all test call sites (6 test files + 2 integ test files)

## Key Design Decisions

1. `wrapPayload()` remains a companion object method (static) but now takes `callbackSignalScriptPath: String` as a parameter. The instance method `sendAndAwaitAck` passes `callbackScriptsDir.signalScriptPath` from the constructor-injected field. This preserves testability of the XML wrapping without needing an instance.

2. `SubPartConfigBuilder.BOOTSTRAP_MESSAGE` changed from `const val` in companion to a `private fun bootstrapMessage()` on the instance. This is necessary because the script path is only known at construction time (from `callbackScriptsDir`), not at compile time.

## Remaining Known Issues (out of scope for this ticket)

- DRY violation: ~15 test files hardcode `/tmp/test-callback-scripts` instead of using `ContextTestFixtures.TEST_CALLBACK_SCRIPTS_DIR`
- Duplicated constants: `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` and `CallbackScriptsDir.REQUIRED_SCRIPT` both hold `"callback_shepherd.signal.sh"`. After this change, `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` has zero production consumers and could be removed.
- `callback_shepherd.query.sh` does not exist as a resource script; `CallbackScriptsDir.queryScriptPath` points to a path that may not exist on disk.

## Test State
- 1946 tests passing, 0 failures
- Detekt passes
