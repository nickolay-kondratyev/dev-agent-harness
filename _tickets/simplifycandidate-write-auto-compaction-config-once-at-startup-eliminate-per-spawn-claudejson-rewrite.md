---
closed_iso: 2026-03-17T23:41:34Z
id: nid_kiqq72tfx38uosam7ig8fbqk9_E
title: "SIMPLIFY_CANDIDATE: Write auto-compaction config once at startup — eliminate per-spawn ~/.claude.json rewrite"
status: closed
deps: []
links: []
created_iso: 2026-03-17T23:14:23Z
status_updated_iso: 2026-03-17T23:41:34Z
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

## Resolution (2026-03-17)

**Spec-only changes** to `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.7bD0uLeoQQSFS16TQeCRF.E):

1. **Config file write moved from per-spawn to startup-only**: `EnvironmentValidator` now reads/writes `~/.claude.json` once at harness startup using Kotlin/Jackson (in-process JSON, no jq dependency). If the file is missing or `autoCompactEnabled` is not `false`, it writes the correct value via atomic write (temp file + rename).

2. **jq dependency eliminated**: Replaced bash jq snippets in spec with Jackson `ObjectMapper` description. Added `WHY-NOT(2026-03-17)` comment documenting why jq was rejected.

3. **Per-spawn env var unchanged**: `ClaudeCodeAgentStarter` continues to export `DISABLE_AUTO_COMPACT=true` in every TMUX session — this is the per-session runtime guarantee and costs nothing.

4. **Belt-and-suspenders preserved**: Both mechanisms (config file + env var) are retained. Only the *when* (startup vs per-spawn) and *how* (Jackson vs jq) of the config file write changed.

**Files modified**: `doc/use-case/ContextWindowSelfCompactionUseCase.md`
**Files NOT modified**: `doc/use-case/SpawnTmuxAgentSessionUseCase.md` (ClaudeCodeAgentStarter section there only mentions env var exports, not config file writes — already correct).