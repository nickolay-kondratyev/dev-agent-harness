# Exploration: PATH Issue for Callback Scripts

## Summary

The PATH for callback scripts is set in `ClaudeCodeAdapter.buildStartCommand()` via:
```kotlin
"export PATH=\$PATH:${callbackScriptsDir.path} && " + claudeCommand
```

This command is wrapped in `bash -c '...'` and sent to tmux. The scripts dir comes from
`ContextInitializerImpl.resolveCallbackScriptsDir()` which extracts `callback_shepherd.signal.sh` from
classpath resources to a temp directory at runtime.

## Key Files

| File | Role |
|------|------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt` | Builds tmux command with PATH export (line 155) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDir.kt` | Validated path wrapper (recently added) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` | Resolves scripts dir (extract from classpath → temp dir) |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestHelpers.kt` | Integ test scripts dir resolution |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/ServerPortInjectingAdapter.kt` | Replaces sentinel values in integ tests |
| `app/src/main/resources/scripts/callback_shepherd.signal.sh` | The callback script itself |
| `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDirTest.kt` | Unit tests for CallbackScriptsDir |

## Recent Fix (already merged to main)

- **Commit `4e2b10bc`**: Replaced raw `String` with `CallbackScriptsDir` validated type
- **Commit `25c2c762`**: Renamed `forTest()` to `unvalidated()` (POLS fix)
- This fix ensures fail-fast validation at construction time (dir exists, script exists, script is executable)
- **Closed ticket**: `nid_kzz296dqtpojvf3gp29827xtk_E` ("fix PATH issue on start")

## Current Gap

No integration test verifies that the PATH is actually set correctly **inside the tmux session**.
The existing `CallbackScriptsDirTest` tests the validation logic (unit tests).
The existing `AgentFacadeImplIntegTest` tests the full agent lifecycle (heavy, requires GLM).

What's missing: a **focused** integration test that:
1. Uses `ClaudeCodeAdapter.buildStartCommand()` to build the command
2. Starts a tmux session (via `TmuxSessionManager`)
3. Verifies PATH contains the callback scripts directory
4. Does NOT require a real Claude agent or GLM

## Command Flow

```
ContextInitializer.initialize()
  → resolveCallbackScriptsDir() → extract script to temp dir → CallbackScriptsDir.validated(tempDir)
  → ClaudeCodeAdapter.create(callbackScriptsDir=...)

AgentFacadeImpl.spawnAgent()
  → adapter.buildStartCommand() → TmuxStartCommand containing:
    bash -c 'cd <workdir> && unset CLAUDECODE && export GUID=... && export PORT=... && export PATH=$PATH:<scriptsDir> && claude ...'
  → tmuxSessionManager.createSessionWithCommand(command)
```
