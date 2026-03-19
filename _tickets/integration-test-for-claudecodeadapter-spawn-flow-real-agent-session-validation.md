---
id: nid_nrws54ce2s2hatkn8nfa3dd7l_E
title: "Integration test for ClaudeCodeAdapter + spawn flow — real agent session validation"
status: in_progress
deps: [nid_t7dnd545hf343i2v7jqcke3x7_E, nid_3rw8eoib2wseydcyhnj648d2a_E]
links: []
created_iso: 2026-03-19T00:17:53Z
status_updated_iso: 2026-03-19T19:53:11Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [integ-test, spawn, agent-adapter]
---

## Context

Spec: `doc/use-case/SpawnTmuxAgentSessionUseCase.md` — Integration Testing section (ref.ap.A0L92SUzkG3gE0gX04ZnK.E)

`ClaudeCodeAdapter` MUST be validated by integration tests against a real Claude Code session to confirm:
- The JSONL file format assumption still holds (Claude Code could change it across versions)
- GUID matching works end-to-end (env var → bootstrap message → JSONL write → scan → resolve)
- Start command construction produces a valid, launchable command

## What To Do

1. Create an integration test that spawns a **single real agent session** and validates multiple concerns from that one session:
   a. Bootstrap handshake completes (`/signal/started` received)
   b. Session ID resolution finds the correct JSONL file with the GUID
   c. Start command produces a working interactive session (not `-p` mode)
   d. Agent can receive `send-keys` input after bootstrap

2. Use `SharedContextDescribeSpec` base class (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) for shared `ShepherdContext`
3. Gate with `.config(isIntegTestEnabled())` per testing standards
4. Use GLM (Z.AI) instead of Claude for agent spawning per `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md`:
   - Set env vars before agent launch: `ANTHROPIC_BASE_URL=https://api.z.ai/api/anthropic`, `ANTHROPIC_AUTH_TOKEN=${Z_AI_GLM_API_TOKEN}`, `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`
   - Map model aliases: `ANTHROPIC_DEFAULT_SONNET_MODEL=glm-5`, `ANTHROPIC_DEFAULT_OPUS_MODEL=glm-5`, `ANTHROPIC_DEFAULT_HAIKU_MODEL=glm-4-flash`
   - Token `Z_AI_GLM_API_TOKEN` is already required by `SharedContextIntegFactory` — no additional setup needed
   - GLM injection should be handled in `ClaudeCodeAdapter.buildStartCommand()` via `environment.isTest` flag

## Resource Efficiency
The spec explicitly states: "Session ID resolution testing should be part of a broader integration test that spawns a single real agent session and validates multiple concerns from that one session. Spawning a separate agent session just for resolver testing would be wasteful."

## Spec References
- `doc/use-case/SpawnTmuxAgentSessionUseCase.md` — Integration Testing section
- `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md` — GLM requirement

## Acceptance Criteria
- Single integration test validates: bootstrap handshake, session ID resolution, start command validity
- Test uses `SharedContextDescribeSpec` and `isIntegTestEnabled()` gate
- Test uses GLM (Z.AI) for agent spawning (not Claude)
- `:app:test -PrunIntegTests=true` passes

