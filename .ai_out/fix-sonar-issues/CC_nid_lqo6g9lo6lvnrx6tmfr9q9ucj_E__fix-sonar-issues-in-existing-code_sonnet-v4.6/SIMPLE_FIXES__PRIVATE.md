# Simple Sonar Fixes - Working Notes

## Status: COMPLETE

## Changes Made

### S6532: Replace if with check()
- ProcessResult.kt: Replaced `if (exitCode != 0) throw IllegalStateException(...)` with `check(exitCode == 0) { ... }`
- EnvironmentValidator.kt line 49: Replaced `if (!Files.exists(dockerEnvFilePath)) throw` with `check(Files.exists(dockerEnvFilePath)) { ... }`
- EnvironmentValidator.kt line 63: Replaced `if (missing.isNotEmpty()) throw` with `check(missing.isEmpty()) { ... }`

### S1192: Extract duplicate string constants
- TmuxCommunicator.kt: Added `companion object { private const val SEND_KEYS = "send-keys" }` and replaced all 3 usages
- ContextForAgentProviderImpl.kt: Added `companion object { private const val SECTION_SEPARATOR = "\n\n---\n\n" }` and replaced all 3 usages

### S6514: Use `by` delegation
- ShepherdContext.kt: Changed `class ShepherdContext(...) : AsgardCloseable { override suspend fun close() { infra.close() } }` to `class ShepherdContext(...) : AsgardCloseable by infra`
- Moved the WHY comment to a class-level comment above the class declaration

### S1172: Remove unused parameter
- AppMain.kt: Changed `fun main(args: Array<String>)` to `fun main()`

### S1135: TODO comment
- Left as-is per instructions (INFO severity, intentional WIP)

## Tests
- BUILD SUCCESSFUL - all tests pass
