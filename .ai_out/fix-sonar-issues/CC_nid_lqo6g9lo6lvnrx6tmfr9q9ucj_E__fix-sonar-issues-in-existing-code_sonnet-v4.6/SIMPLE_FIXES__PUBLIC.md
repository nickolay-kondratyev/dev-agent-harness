# Simple Sonar Fixes - Summary

## Status: COMPLETE - All tests pass

## Changes Made

### S6532: Replace if with check() (3 fixes)

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/util/ProcessResult.kt`
- Replaced `if (exitCode != 0) throw IllegalStateException(...)` with `check(exitCode == 0) { ... }`

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt`
- Replaced `if (!Files.exists(dockerEnvFilePath)) throw` with `check(Files.exists(dockerEnvFilePath)) { ... }`
- Replaced `if (missing.isNotEmpty()) throw` with `check(missing.isEmpty()) { ... }`

### S1192: Extract duplicate string constants (2 fixes)

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxCommunicator.kt`
- Added `companion object { private const val SEND_KEYS = "send-keys" }` to `TmuxCommunicatorImpl`
- Replaced all 3 usages of `"send-keys"` with `SEND_KEYS`

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- Added `companion object { private const val SECTION_SEPARATOR = "\n\n---\n\n" }` to `ContextForAgentProviderImpl`
- Replaced all 3 usages of `"\n\n---\n\n"` with `SECTION_SEPARATOR`

### S6514: Use `by` delegation (1 fix)

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt`
- Changed class declaration from `class ShepherdContext(...) : AsgardCloseable { override suspend fun close() { infra.close() } }` to `class ShepherdContext(...) : AsgardCloseable by infra`
- The WHY comment about infra being closed last was preserved as a class-level comment

### S1172: Remove unused parameter (1 fix)

**File**: `app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt`
- Changed `fun main(args: Array<String>)` to `fun main()` since `args` was not used

### S1135: TODO comment (intentionally skipped)
- Left as-is: INFO severity, intentional WIP marker for unimplemented CLI

## Test Results

BUILD SUCCESSFUL - all tests pass (detekt + unit tests via `./test.sh`)
