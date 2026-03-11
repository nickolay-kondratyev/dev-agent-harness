---
id: nid_a4g8sezxri8ex9p7sha44ggdr_E
title: "Clean up Wingman terminology: rename package and remaining references to AgentSessionIdResolver"
status: in_progress
deps: []
links: []
created_iso: 2026-03-11T15:29:27Z
status_updated_iso: 2026-03-11T15:31:14Z
type: chore
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [refactor, naming]
---

## Context

The class/interface renames from Wingman to AgentSessionIdResolver have already been done.
What remains is cleaning up the **package name**, **test class name**, **variable names**, **comments**, and **documentation**.

## What is Already Correct (no changes needed)
- `AgentSessionIdResolver` interface
- `ClaudeCodeAgentSessionIdResolver` class
- `AgentStarterBundle.sessionIdResolver` field
- `SpawnTmuxAgentSessionUseCase` logic (uses `bundle.sessionIdResolver`)

## Requirements

### R1 — Package rename
Rename package `com.glassthought.chainsaw.core.wingman` → `com.glassthought.chainsaw.core.sessionresolver`.
Affects 4 source files:
- `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/AgentSessionIdResolver.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/impl/ClaudeCodeAgentSessionIdResolver.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/HandshakeGuid.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ResumableAgentSessionId.kt`

And all files importing from the old package:
- `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentStarterBundle.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/TmuxAgentSession.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCase.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactory.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/data/AgentType.kt` (KDoc reference)

### R2 — Test file rename
Rename `app/src/test/kotlin/com/glassthought/chainsaw/core/wingman/impl/ClaudeCodeWingmanTest.kt`
→ `app/src/test/kotlin/com/glassthought/chainsaw/core/sessionresolver/impl/ClaudeCodeAgentSessionIdResolverTest.kt`
Update class name and package declaration.

### R3 — Local variable names in tests
All `val wingman = ClaudeCodeAgentSessionIdResolver(...)` → `val resolver = ...`

### R4 — Test helper prefix
`createTempDirectory("wingman-test-")` → `createTempDirectory("session-resolver-test-")`

### R5 — Test describe strings
`"GIVEN a ClaudeCodeWingman..."` → `"GIVEN a ClaudeCodeAgentSessionIdResolver..."`

### R6 — doc/high-level.md
Update all "Wingman" references to "AgentSessionIdResolver":
- Section title "Session ID Tracking — Wingman" → "Session ID Tracking — AgentSessionIdResolver"
- Body text, lifecycle steps, technology decisions table
- Comment anchor ref text: `ClaudeCodeWingman` → `ClaudeCodeAgentSessionIdResolver`

### R7 — ai_input/memory/auto_load/1_core_description.md
Update the line: `**Session tracking**: \`Wingman\` interface (\`ClaudeCodeWingman\` impl)` to use new names.

### R8 — AgentSessionIdResolver.kt KDoc
Update `"See ref.ap.gCgRdmWd9eTGXPbHJvyxI.E for the ClaudeCodeWingman implementation"` → `ClaudeCodeAgentSessionIdResolver`

### R9 — Regenerate CLAUDE.md
Run `./CLAUDE.generate.sh` after doc changes.

### R10 — All tests pass
`./gradlew :app:test` must be green.

## Verification
- `grep -ri wingman` across `doc/`, `ai_input/`, and `app/src/` returns zero hits.
- `./gradlew :app:compileKotlin :app:compileTestKotlin` compiles clean.
- `./gradlew :app:test` passes.

## Out of Scope
- `.ai_out/` directories (historical agent outputs)
- `_tickets/` (closed/historical tickets)
- `_change_log/` (historical entries)
- `app/test-results/` (regenerated on next test run)
- Anchor point IDs themselves (APs are stable; only description text at definition site gets updated)

