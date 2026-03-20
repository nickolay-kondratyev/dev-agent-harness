# Planner Private Notes

## Analysis Summary

The problem is well-scoped: write a focused integration test that verifies PATH is set correctly inside tmux sessions.

## Key Implementation Details for the Implementer

### Command Replacement Strategy

The generated command from `ClaudeCodeAdapter.buildStartCommand()` looks like:
```
bash -c 'cd /path/to/workdir && unset CLAUDECODE && export TICKET_SHEPHERD_HANDSHAKE_GUID=path-test-guid && export TICKET_SHEPHERD_SERVER_PORT=99999 && export PATH=$PATH:/real/scripts/dir && claude --model sonnet --dangerously-skip-permissions "bootstrap...\n\n[HARNESS_GUID: path-test-guid]"'
```

The replacement needs to swap `claude --model sonnet ... "bootstrap..."` with `echo $PATH > /path/to/output`.

**Safest approach**: Find `claude --model` in the command string. Replace from there to the end, preserving the closing `'`.

```kotlin
val claudeCommandStart = command.indexOf("claude --model")
val closingQuote = command.lastIndexOf("'")
val modifiedCommand = command.substring(0, claudeCommandStart) +
    "echo \$PATH > ${outputFile.absolutePath}" +
    command.substring(closingQuote)
```

WAIT -- inside `bash -c '...'`, the `$PATH` is already literal (single quotes prevent expansion by the OUTER shell). The INNER bash (invoked by `bash -c`) will expand `$PATH`. So `echo $PATH > file` inside the single quotes is correct.

But the escapeForBashC function replaces `'` with `'\''`. So if the outputFile path contains no single quotes (which it won't for `.tmp/` paths), this is safe.

Actually, let me reconsider whether we even need to go through `escapeForBashC`. We are modifying the command AFTER `buildStartCommand()` has already produced the final `bash -c '...'` string. So we are doing string surgery on the outer command. The `$PATH` inside the existing single-quoted wrapper will be expanded by the inner bash. Our `echo $PATH > file` is just replacing the `claude ...` portion and will also be inside the single quotes. This is correct.

### Which GuidScanner to use

Use the functional interface shorthand: `GuidScanner { emptyList() }` -- same pattern as `ClaudeCodeAdapterTest` line 289.

### ClaudeCodeAdapter instantiation

Use the internal constructor directly (accessible from test code in the same module):
```kotlin
ClaudeCodeAdapter(
    guidScanner = GuidScanner { emptyList() },
    outFactory = outFactory,  // from AsgardDescribeSpec
    serverPort = 99999,
    callbackScriptsDir = scriptsDir,
)
```

Actually wait -- `SharedContextDescribeSpec` inherits from `AsgardDescribeSpec` which provides `outFactory`. But the `ClaudeCodeAdapter` constructor is `internal` -- accessible from `app` module tests? Yes, the test is in the same module (`app`), so `internal` is accessible.

### `BuildStartCommandParams` dummy values

```kotlin
BuildStartCommandParams(
    bootstrapMessage = "path-integration-test",
    handshakeGuid = HandshakeGuid("path-test-guid"),
    workingDir = System.getProperty("user.dir"),
    model = "sonnet",
    tools = emptyList(),
    systemPromptFilePath = null,
    appendSystemPrompt = false,
)
```

### AsgardAwaitility import

From `TmuxCommunicatorIntegTest`:
```kotlin
import com.asgard.testTools.awaitility.AsgardAwaitility
```

### Risks

1. **PATH could have trailing newline** -- `echo` adds a newline. Use `.trim()` on file contents before assertion, or use `shouldContain` which handles substrings.
2. **Session creation timing** -- tmux `new-session` is synchronous, so by the time `createSession` returns, the bash command has started. The `echo` completes almost instantly. Polling with 5s timeout is very generous.
3. **The `bash -c` exits immediately after echo** -- This means the tmux session will die. `session.exists()` might return false by the time we check cleanup. The `afterEach` cleanup handles this with try/catch.

## Decision Record

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Test location | `integtest` package | Application-level integration (adapter + tmux), not infra-only |
| Base class | `SharedContextDescribeSpec` | Needs `shepherdContext.infra.tmux.sessionManager` |
| Command modification | String surgery on `buildStartCommand()` output | Tests real command pipeline; minimal synthetic deviation |
| Verification | Write PATH to file, poll, read, assert contains | Simple, deterministic, follows existing `TmuxCommunicatorIntegTest` pattern |
| Adapter instantiation | Direct `internal` constructor with no-op GuidScanner | Avoids needing sentinel port replacement via `ServerPortInjectingAdapter` |

## Files to Create

1. `app/src/test/kotlin/com/glassthought/shepherd/integtest/TmuxPathIntegTest.kt` -- the new test

## Files NOT Modified

No existing files need modification. This is purely additive.
