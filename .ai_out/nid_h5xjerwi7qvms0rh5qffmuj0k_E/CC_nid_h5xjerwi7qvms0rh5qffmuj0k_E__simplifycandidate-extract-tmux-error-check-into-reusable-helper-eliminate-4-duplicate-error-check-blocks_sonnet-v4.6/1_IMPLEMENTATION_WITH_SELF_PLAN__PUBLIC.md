# Implementation: Extract tmux error-check into reusable helper

## What was changed

### 1. Added `orThrow` extension function — `ProcessResult.kt`

Added an `internal` extension function `ProcessResult.orThrow(operation: String)` to
`app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/util/ProcessResult.kt`.

The function throws `IllegalStateException` when `exitCode != 0`, producing an error message
in the exact same format as all previous inline checks:
```
Failed to $operation. Exit code: [$exitCode]. Stderr: [$stdErr]
```

### 2. Replaced 2 inline error-check blocks — `TmuxSessionManager.kt`

- Added `import com.glassthought.shepherd.core.agent.tmux.util.orThrow`
- `createSession`: replaced 5-line `val result = ... if (result.exitCode != 0) { throw ... }` block
  with a 2-line chained `.orThrow(...)` call.
- `killSession`: same replacement.

### 3. Replaced 3 inline error-check blocks — `TmuxCommunicator.kt`

- Added `import com.glassthought.shepherd.core.agent.tmux.util.orThrow`
- `sendKeys` literal send: replaced `literalResult` variable + check with chained `.orThrow(...)`.
- `sendKeys` Enter send: replaced `enterResult` variable + check with chained `.orThrow(...)`.
- `sendRawKeys`: replaced `result` variable + check with chained `.orThrow(...)`.

## Error message preservation verification

| Site | `operation` argument | Resulting message prefix |
|------|----------------------|--------------------------|
| `createSession` | `"create tmux session [$sessionName] with command [${startCommand.command}]"` | `"Failed to create tmux session [...] with command [...]."` |
| `killSession` | `"kill tmux session [${session.name.sessionName}]"` | `"Failed to kill tmux session [...]."` |
| `sendKeys` literal | `"send literal keys to tmux pane [$paneTarget]"` | `"Failed to send literal keys to tmux pane [...]."` |
| `sendKeys` Enter | `"send Enter to tmux pane [$paneTarget]"` | `"Failed to send Enter to tmux pane [...]."` |
| `sendRawKeys` | `"send raw keys to tmux pane [$paneTarget]"` | `"Failed to send raw keys to tmux pane [...]."` |

All messages are character-for-character identical to the originals.

## Compilation

`./gradlew :app:compileKotlin` exited with code **0** — compilation succeeded.

## Files modified

- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/util/ProcessResult.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxCommunicator.kt`

## Issues found

None. Zero behavior change — pure structural DRY improvement eliminating 5 duplicate error-check
blocks (the task description says 4, but there are actually 5 call sites: 2 in `TmuxSessionManager`
and 3 in `TmuxCommunicatorImpl`).
