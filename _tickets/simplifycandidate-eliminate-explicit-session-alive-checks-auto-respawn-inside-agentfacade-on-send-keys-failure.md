---
closed_iso: 2026-03-17T21:40:30Z
id: nid_vutc5ggsha72m7rhrbiyrsb28_E
title: "SIMPLIFY_CANDIDATE: Eliminate explicit session-alive checks — auto-respawn inside AgentFacade on send-keys failure"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:23:23Z
status_updated_iso: 2026-03-17T21:40:30Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, session-lifecycle, robustness, tmux]
---


## Notes

**2026-03-17T21:23:50Z**

PartExecutorImpl distinguishes first-run (spawn) vs re-instruction (send to live session). After self-compaction or idle session death the executor must also detect no live handle and respawn — duplicated dead-session-detection logic. See: doc/core/PartExecutor.md, doc/use-case/ContextWindowSelfCompactionUseCase.md. 


Simpler approach: AgentFacade.sendPayloadAndAwaitSignal() (companion ticket) transparently detects send-keys failure: attempt send-keys; on failure auto-spawn new session; return signal from whichever session received the payload. Executor calls one method regardless of session state.


Benefits: unifies three respawn triggers into one place, executor agnostic to session lifecycle, easier to test.

**2026-03-17T21:40:38Z**

Completed. Spec-only change across three documents:

1. AgentInteraction.md: Replaced spawnAgent()+sendPayloadWithAck() with unified sendPayload(config, existingHandle?, payload): SpawnedAgentHandle. The method transparently handles session lifecycle (spawn/reuse/respawn). Always returns handle with fresh signal deferred, resolving the open R1 question.

2. PartExecutor.md: Removed if(liveHandleExists) branch. Executor always calls sendPayload() regardless of session state. Idle session death (V1 crash) replaced with transparent respawn via facade.

3. ContextWindowSelfCompactionUseCase.md: Removed the if/else code block. Compaction flow simplified — reset+spawn+send collapse into single sendPayload(config, null, instructions) call.

All three respawn triggers (first-run, post-compaction, idle-session-death) now live in AgentFacade.
