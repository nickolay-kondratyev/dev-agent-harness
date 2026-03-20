# Exploration: PATH Issue with callback_shepherd.signal.sh

## Problem
Agents spawned in TMUX get exit code 127 (command not found) when calling `callback_shepherd.signal.sh`.

## Root Cause
No validation that `callbackScriptsDir` exists and contains the expected script before adding it to PATH in the tmux command.

## Key Files
- `ClaudeCodeAdapter.kt` - builds tmux command, adds `callbackScriptsDir` to PATH at line 153
- `ContextInitializerImpl.resolveCallbackScriptsDir()` - extracts script from classpath to temp dir (lines 242-269)
- `ClaudeCodeAdapterTest.kt` - existing tests use fake path `/opt/shepherd/scripts` without existence validation

## Fix Approach
1. Add a `CallbackScriptsDir` value class that validates directory exists and contains the script
2. Validate at construction time (fail-fast) rather than at command-build time
3. Add tests for validation behavior
