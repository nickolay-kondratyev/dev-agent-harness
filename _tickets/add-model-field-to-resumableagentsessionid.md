---
closed_iso: 2026-03-11T21:11:00Z
id: nid_pem1dso3jg39azl0v9lpzjvf4_E
title: "Add model field to ResumableAgentSessionId"
status: closed
deps: []
links: []
created_iso: 2026-03-11T00:31:20Z
status_updated_iso: 2026-03-11T21:11:00Z
type: task
priority: 2
assignee: nickolaykondratyev
tags: [agent, resume]
---

## Why

Claude Code cannot switch models when resuming a session. If a session was started with `--model sonnet`, resuming it without `--model sonnet` may default to a different model, breaking continuity.

`ResumableAgentSessionId` is persisted to `session_ids/${timestamp}.json` and used by `ResumeTmuxAgentSessionUseCase` to build the resume command. Without the model field, the resume path has no way to know which model to pass.

## What

Add `val model: String` to `ResumableAgentSessionId` in:
`app/src/main/kotlin/com/glassthought/shepherd/core/wingman/ResumableAgentSessionId.kt`

This enables `ResumeTmuxAgentSessionUseCase` to build: `claude --resume <sessionId> --model <model>`

## Coordination

This class is currently used by in-flight code on other branches. Defer this change until those branches land to avoid merge conflicts.


## Notes

**2026-03-11T21:11:00Z**

Completed. Added val model: String to ResumableAgentSessionId. Injected via ClaudeCodeAgentSessionIdResolver constructor from ClaudeCodeAgentStarterBundleFactory. AgentSessionIdResolver interface unchanged. All tests pass. See change_log l655ewclkpflp9113i7nvlr9i.
