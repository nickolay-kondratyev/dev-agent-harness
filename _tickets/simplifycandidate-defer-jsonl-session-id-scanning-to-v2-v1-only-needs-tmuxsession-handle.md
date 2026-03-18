---
closed_iso: 2026-03-18T15:24:55Z
id: nid_3vkkp3iid3lwagfrfeqt8avfp_E
title: "SIMPLIFY_CANDIDATE: Defer JSONL session ID scanning to V2 — V1 only needs TmuxSession handle"
status: closed
deps: []
links: []
created_iso: 2026-03-18T15:09:05Z
status_updated_iso: 2026-03-18T15:24:55Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, spawn, robustness]
---

## Problem

The `ClaudeCodeAdapter` in `doc/use-case/SpawnTmuxAgentSessionUseCase.md` includes JSONL file scanning to resolve `ResumableAgentSessionId`:
- Polls `$HOME/.claude/projects/.../*.jsonl` files
- 45-second polling with 500ms intervals
- Depends on Claude Code internal file layout (fragile — breaks on CC version changes)
- Requires the `unset CLAUDECODE` workaround hack in start commands

The `TmuxAgentSession` data structure bundles both `TmuxSession` (harness-created, always available) and `ResumableAgentSessionId` (JSONL-scanned, fragile). But `ResumableAgentSessionId` is ONLY needed for V2 resume (`--resume <session_id>`).

In V1, sessions are never resumed — they are always spawned fresh.

## Proposed Simplification

For V1:
1. Remove `resolveSessionId` from `AgentTypeAdapter` interface
2. Simplify `TmuxAgentSession` to just wrap `TmuxSession`
3. Skip JSONL scanning entirely
4. Remove the `ResumableAgentSessionId` concept from V1 code

Re-introduce JSONL scanning when V2 resume is actually implemented.

## Why This Is Both Simpler AND More Robust
- **Simpler**: Removes polling logic, JSONL parsing, `ResumableAgentSessionId` type, and the `resolveSessionId` method from the V1 interface
- **More robust**: Eliminates dependency on Claude Code internal file layout. No more silent breakage when CC changes its JSONL directory structure. No more 45-second polling that "typically resolves instantly" based on timing assumptions
- **YAGNI**: V2 resume is explicitly deferred. Building the scanning infrastructure now is premature

## Related
- Partially related to existing ticket "Move V2 design content from V1 specs to doc_v2/" but that ticket focuses on spec docs, not code/interface simplification

