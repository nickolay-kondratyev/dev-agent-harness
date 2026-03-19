---
id: nid_dufv6609fbbksmai08h5stqk7_E
title: "Integration test — self-compaction end-to-end with real agent session"
status: open
deps: [nid_74c83cczud9jnwk1l1ywc8w2y_E]
links: []
created_iso: 2026-03-19T00:42:54Z
status_updated_iso: 2026-03-19T00:42:54Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, compaction, integration-test]
---

## Context

Spec: `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E), Gate 4.

End-to-end integration validation of the self-compaction flow with a real Claude Code agent.

## What to Implement

Location: `app/src/test/kotlin/com/glassthought/shepherd/integtest/compaction/`

An integration test class that validates the full self-compaction flow against real infrastructure.

### Test Scenarios

1. **context_window_slim.json is present and readable** after a real Claude Code session starts
   - Spawn agent → wait for first callback → read context_window_slim.json → verify it parses correctly

2. **Self-compaction can be triggered at done boundary**
   - This may require a specifically crafted scenario that fills context, OR a test that directly invokes performCompaction with a real session

3. **Session rotation produces a working new session with PRIVATE.md**
   - After compaction: old session killed → new session spawned → verify PRIVATE.md content is in new instructions

### Test Configuration

- Extend `SharedContextDescribeSpec` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E)
- Gate with `isIntegTestEnabled()` — requires real tmux, real agent
- Use GLM for agent spawning (not Claude directly) per deep memory `integ_tests__use_glm_for_agent_spawning.md`

### Note

This is a validation step, not a development step. All unit tests should pass before this integration test is written. The purpose is to confirm that file paths, JSON parsing, TMUX session management, and the full flow work correctly against real infrastructure.

## Acceptance Criteria

- Integration test validates context_window_slim.json readability
- Integration test validates session rotation with PRIVATE.md
- Tests gated behind isIntegTestEnabled()
- `./test.sh` passes (integ tests skipped in normal runs)

