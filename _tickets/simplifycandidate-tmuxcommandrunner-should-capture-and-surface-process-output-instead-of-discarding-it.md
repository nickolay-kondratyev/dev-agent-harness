---
id: nid_qf6iicpkgyd6qxjfccgy9ch4t_E
title: "SIMPLIFY_CANDIDATE: TmuxCommandRunner should capture and surface process output instead of discarding it"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:39:05Z
status_updated_iso: 2026-03-17T21:39:05Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, tmux, robustness, debugging]
---

TmuxCommandRunner (app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/util/TmuxCommandRunner.kt) drains stdout and stderr to prevent buffer blocking but discards the captured bytes entirely.

Currently:
```kotlin
process.inputStream.readBytes()  // discarded
process.errorStream.readBytes()  // discarded
```

When a tmux command fails (e.g., session already exists, invalid pane target, permission error), the exit code is returned but the actual error message from tmux is silently lost. Callers only see `exitCode \!= 0` with no diagnostic context.

**Simplification:** Return a `ProcessResult(val exitCode: Int, val stdOut: String, val stdErr)` data class instead of a raw `Int`. This is simpler (one return type that captures everything) and callers can surface the output in error logs:

```kotlin
data class ProcessResult
```

**Robustness improvement:** Session lifecycle errors (send-keys to dead session, tmux version mismatches, permission failures) currently produce opaque failures with no diagnostic info. With ProcessResult, the harness can log the actual tmux error, dramatically reducing time-to-debug production issues.

