# Implementation Iteration: Fix Shell Escaping Bug

## Issue Addressed

**MAJOR bug from review**: In `ClaudeCodeAgentStarter.buildStartCommand()`, the `claudeCommand` string was interpolated directly into the outer `bash -c '...'` single-quoted wrapper without escaping single quotes. Any single quote in the system prompt (e.g., `You're`), model name, or tool name would break the outer single-quoting, causing either a syntax error or potential command injection.

## Root Cause

The original code applied `escapeForShell()` only to `workingDir`, not to the rest of the inner command:

```kotlin
val fullCommand = "bash -c 'cd ${escapeForShell(workingDir)} && unset CLAUDECODE && $claudeCommand'"
```

## Fix Applied

Build the complete inner command as a plain string, then apply `escapeForShell()` once to the entire inner command:

```kotlin
val innerCommand = "cd $workingDir && unset CLAUDECODE && $claudeCommand"
val fullCommand = "bash -c '${escapeForShell(innerCommand)}'"
```

This ensures that **all** single quotes anywhere in the inner command (workingDir, system prompt, model, tools) are properly escaped for the outer single-quote context using the `'\''` idiom.

## Tests Added

1. **System prompt with single quotes**: Verifies that `You're a test agent. Don't do anything unexpected.` produces properly escaped output with `'\''` idiom for each single quote.
2. **WorkingDir with single quote**: Verifies that `/home/user/it's-a-project` is properly escaped.
3. **Structural validation**: Verifies the command starts with `bash -c '` and ends with `'`, and that the system prompt remains double-quoted within the inner command.

## Test Results

All 28 tests pass (23 existing + 5 new):
- `ClaudeCodeAgentStarterTest` -- 13 tests (8 existing + 5 new)
- `DefaultAgentTypeChooserTest` -- 4 tests
- `ClaudeCodeAgentStarterBundleFactoryTest` -- 8 tests
- `EnvironmentTest` -- 3 tests

## Review Suggestions Not Addressed (by design)

1. **ProcessBuilder instead of string-based command** -- Reviewer acknowledged this is a larger refactor, not needed for this ticket.
2. **`delay` in SpawnTmuxAgentSessionUseCase** -- Reviewer acknowledged this is acceptable for V1 with configurable default.
3. **Verbose logging at INFO level** -- Minor suggestion, not blocking. Existing logging is thorough and useful during early development.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarter.kt` | Escape entire inner command instead of just workingDir |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarterTest.kt` | Added 2 new describe blocks (5 new test cases) for single-quote escaping |

## Commit

`9b9d8b4` - Fix shell escaping: escape entire inner command for single-quote context
