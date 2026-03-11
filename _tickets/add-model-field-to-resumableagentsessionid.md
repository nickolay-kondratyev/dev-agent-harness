---
closed_iso: 2026-03-11T21:06:48Z
id: nid_pem1dso3jg39azl0v9lpzjvf4_E
title: "Add model field to ResumableAgentSessionId"
status: closed
deps: []
links: []
created_iso: 2026-03-11T00:31:20Z
status_updated_iso: 2026-03-11T21:06:48Z
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

