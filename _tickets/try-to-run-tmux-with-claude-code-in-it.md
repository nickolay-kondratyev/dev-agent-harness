---
closed_iso: 2026-03-07T19:28:43Z
id: nid_zj8rww86omnzhz23lzk8znyf0_E
title: "Try to run tmux with claude code in it"
status: closed
deps: []
links: []
created_iso: 2026-03-07T19:10:48Z
status_updated_iso: 2026-03-07T19:28:43Z
type: task
priority: 3
assignee: nickolaykondratyev
---

## Resolution

Implemented tmux session management and keystroke sending from Kotlin.

### New Files
- `app/src/main/kotlin/org/example/TmuxSessionManager.kt` — Creates, queries, and kills tmux sessions via ProcessBuilder
- `app/src/main/kotlin/org/example/TmuxCommunicator.kt` — Sends keystrokes to tmux sessions using `tmux send-keys -l` (literal text) + `Enter`
- `app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt` — 4 integration tests for session lifecycle
- `app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt` — 1 integration test verifying keystroke delivery

### Modified Files
- `app/src/main/kotlin/org/example/App.kt` — Now creates tmux session `agent-harness__<timestamp>`, runs `claude` in it, sends test prompt

### How It Works
1. `App.main()` creates a detached tmux session named `agent-harness__<millis>` running `claude`
2. Prints session name to stdout
3. Sends "Write 'hello world from tmux' to /tmp/out" via `TmuxCommunicator.sendKeys()`
4. All 12 tests pass (4 new TmuxSessionManager + 1 new TmuxCommunicator + 7 existing)

### Key Design Decisions
- Used `-l` flag in `tmux send-keys` so literal text (containing words like "Enter", "Space") is not misinterpreted as tmux key names
- Session cleanup via `@AfterEach` in tests
- `InteractiveProcessRunner` preserved as-is for direct TTY use case

---

## Original Description

We are able to run Claude from kotlin with interactivity (IF we are not running under Gradle).

What we now want is to be able to run brand new tmux session from kotlin lets name it something like agent-harness__$XX

And run Claude in that tmux session.