---
closed_iso: 2026-03-17T23:07:02Z
id: nid_h5xjerwi7qvms0rh5qffmuj0k_E
title: "SIMPLIFY_CANDIDATE: Extract Tmux error-check into reusable helper — eliminate 4 duplicate error-check blocks"
status: closed
deps: []
links: []
created_iso: 2026-03-17T22:46:35Z
status_updated_iso: 2026-03-17T23:07:02Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, dry, tmux, robustness]
---

## Problem
TmuxSessionManager and TmuxCommunicator each contain identical error-check patterns repeated 4 times:

```kotlin
val result = commandRunner.run(...)
if (result.exitCode \!= 0) {
    throw IllegalStateException(
        "Failed to [operation]. Exit code: [${result.exitCode}]. Stderr: [${result.stdErr}]"
    )
}
```

Files affected:
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt` (2 blocks)
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxCommunicator.kt` (2 blocks)

## Proposed Simplification
Extract to an extension function on the result type:

```kotlin
private fun ProcessRunnerResult.orThrow(operation: String) {
    if (exitCode \!= 0)
        throw IllegalStateException("Failed to $operation. Exit code: [$exitCode]. Stderr: [$stdErr]")
}
```

Each call site becomes: `commandRunner.run(...).orThrow("create tmux session")`

## Why This Improves Both
- **Simpler**: 4 blocks → 4 one-liners
- **More robust**: Error message format guaranteed consistent; future improvements (e.g., logging) applied once
- **Zero behavior change**: Same exception type and message format

## Acceptance Criteria

- Error-check blocks in TmuxSessionManager and TmuxCommunicator replaced by calls to shared helper
- All existing tmux-related tests pass
- No behavior change in error messages


## Notes

**2026-03-17T23:07:07Z**

RESOLVED: Extracted ProcessResult.orThrow(operation: String) internal extension function into ProcessResult.kt. Replaced 5 duplicate error-check blocks (2 in TmuxSessionManager, 3 in TmuxCommunicatorImpl). Zero behavior change — error messages preserved character-for-character. Added ProcessResultTest unit test covering success and failure paths. All tests pass.
