# Implementation: Fix PATH Issue on Start

## What Was Done

Added `CallbackScriptsDir` validated type that ensures the callback scripts directory exists and contains an executable `callback_shepherd.signal.sh` at construction time. This prevents agents from getting exit code 127 (command not found) when the PATH is misconfigured.

### Changes

1. **New class `CallbackScriptsDir`** — validates directory existence, script presence, and executable permission at construction time.
   - `CallbackScriptsDir.validated(path)` — production factory with full validation
   - `CallbackScriptsDir.forTest(path)` — test factory that skips filesystem validation

2. **Updated `ClaudeCodeAdapter`** — accepts `CallbackScriptsDir` instead of `String` for the callback scripts dir parameter. Uses `.path` property when building the PATH export command.

3. **Updated `ContextInitializerImpl.resolveCallbackScriptsDir()`** — returns `CallbackScriptsDir.validated()` for production path (after extracting script from classpath) and `CallbackScriptsDir.forTest()` for override path.

4. **Updated all test files** — use `CallbackScriptsDir.forTest()` for unit tests with fake paths, and `CallbackScriptsDir.validated()` in integration test helpers.

5. **Added `CallbackScriptsDirTest`** — BDD-style tests covering:
   - Valid directory with executable script
   - Nonexistent directory
   - Missing script in directory
   - Non-executable script
   - File path instead of directory
   - `forTest` factory skipping validation
   - Equality and hashCode

## Test Results

`./test.sh` — BUILD SUCCESSFUL. All tests pass including new `CallbackScriptsDirTest`.

## Files Modified

- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDir.kt` (NEW)
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDirTest.kt` (NEW)
- `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapterTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestHelpers.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/ServerPortInjectingAdapter.kt`

## Design Decision

Used a regular class with private constructor and companion factory methods instead of `@JvmInline value class`. Reason: inline value classes require a public constructor, which would not allow the `forTest`/`validated` factory pattern needed to skip validation in unit tests. The class is lightweight and the overhead vs inline is negligible.
