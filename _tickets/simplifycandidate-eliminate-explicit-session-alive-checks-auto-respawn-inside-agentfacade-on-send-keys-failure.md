---
id: nid_vutc5ggsha72m7rhrbiyrsb28_E
title: "SIMPLIFY_CANDIDATE: Eliminate explicit session-alive checks — auto-respawn inside AgentFacade on send-keys failure"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:23:23Z
status_updated_iso: 2026-03-17T21:23:23Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, session-lifecycle, robustness, tmux]
---


## Notes

**2026-03-17T21:23:50Z**

PartExecutorImpl distinguishes first-run (spawn) vs re-instruction (send to live session). After self-compaction or idle session death the executor must also detect no live handle and respawn — duplicated dead-session-detection logic. See: doc/core/PartExecutor.md, doc/use-case/ContextWindowSelfCompactionUseCase.md. Simpler approach: AgentFacade.sendPayloadAndAwaitSignal() (companion ticket) transparently detects send-keys failure: attempt send-keys; on failure auto-spawn new session; return signal from whichever session received the payload. Executor calls one method regardless of session state. Benefits: unifies three respawn triggers into one place, executor agnostic to session lifecycle, easier to test.
