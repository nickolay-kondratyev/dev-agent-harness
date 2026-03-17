---
id: nid_kiqq72tfx38uosam7ig8fbqk9_E
title: "SIMPLIFY_CANDIDATE: Write auto-compaction config once at startup — eliminate per-spawn ~/.claude.json rewrite"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T23:14:23Z
status_updated_iso: 2026-03-17T23:38:07Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

FEEDBACK:
--------------------------------------------------------------------------------
In doc/use-case/ContextWindowSelfCompactionUseCase.md (ref.ap.7bD0uLeoQQSFS16TQeCRF.E, lines 405-451), the auto-compaction disable mechanism uses belt-and-suspenders:

1. **Startup**: EnvironmentValidator validates ~/.claude.json contains "autoCompactEnabled": false
2. **Every spawn**: ClaudeCodeAgentStarter (a) rewrites ~/.claude.json via jq AND (b) exports DISABLE_AUTO_COMPACT=true env var

The per-spawn config file rewrite involves:
- Reading ~/.claude.json
- Running jq to set autoCompactEnabled = false
- Writing to a temp file
- Renaming temp to ~/.claude.json
- This happens on EVERY spawn including session rotations

**Problem:** The per-spawn config file rewrite adds complexity and failure modes:
1. jq must be available (additional dependency)
2. File I/O on every spawn — temp file + rename can fail (disk full, permissions)
3. Race condition if two agents spawn simultaneously and both try to rewrite the same file
4. The spec justifies this with "guards against external processes re-enabling compaction" — but no external process touches ~/.claude.json during a harness run

**Proposed simplification:** Write ~/.claude.json ONCE at harness startup (in EnvironmentValidator or early initialization). Keep the per-spawn DISABLE_AUTO_COMPACT=true env var export (it is per-TMUX-session and costs nothing).

**Why this works just as well or better:**
- The harness is the sole manager of Claude Code sessions. No external process re-enables auto-compaction mid-run.
- The env var (DISABLE_AUTO_COMPACT=true) is the runtime enforcement — it is already exported per spawn and provides the per-session guarantee
- The config file is the persistent setting — writing it once at startup is sufficient since the harness process lifetime bounds the entire run
- If somehow the file is modified externally, the env var still prevents auto-compaction

**Robustness improvement:**
- Eliminates jq dependency from spawn path
- Eliminates file I/O failure modes on every spawn
- Eliminates race condition on concurrent spawns
- Simpler spawn command (no jq pipe + temp file + mv)
- Keeps the env var as the reliable per-session guarantee

Affected specs:
- doc/use-case/ContextWindowSelfCompactionUseCase.md (Harness Responsibilities table, ClaudeCodeAgentStarter section)
- doc/use-case/SpawnTmuxAgentSessionUseCase.md (ClaudeCodeAgentStarter)
--------------------------------------------------------------------------------

Belt and suspenders are fine here as we arent confidient in Claude's Codes respect of settings. However, what we SHOULD simplify out is things like JQ dependency we are in kotlin and we can use things like Jackson for JSON serialization. 