---
id: nid_6n35nlejj5yy8iu4q7efxzbej_E
title: "SIMPLIFY_CANDIDATE: Single auto-compaction disablement — env var only, drop .claude.json config"
status: open
deps: []
links: []
created_iso: 2026-03-18T02:22:59Z
status_updated_iso: 2026-03-18T02:22:59Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, compaction, robustness]
---

Feedback:
--------------------------------------------------------------------------------
## Problem

The self-compaction spec (ref.ap.8nwz2AHf503xwq8fKuLcl.E) disables Claude Code auto-compaction via TWO mechanisms:
1. `~/.claude.json` with `autoCompactEnabled: false`
2. `DISABLE_AUTO_COMPACT=true` environment variable

The spec calls this "belt and suspenders" but it creates:
- Two things to configure correctly
- Two things that can go wrong silently (spec itself warns `.claude/settings.json` silently ignores the key)
- Two things to debug when compaction happens unexpectedly
- A file-system dependency (`.claude.json`) outside the repo/harness control

## Proposed Simplification

Use ONLY the `DISABLE_AUTO_COMPACT=true` env var:
- Harness already controls the TMUX session environment (sets `TICKET_SHEPHERD_SERVER_PORT`, `HANDSHAKE_GUID`, etc.)
- Adding one more env var is zero additional complexity
- Env var is set per-session, not per-machine — no host-level config dependency
- Single source of truth: if compaction isn't disabled, check the env var\n\n## What Gets Removed\n- `.claude.json` configuration requirement\n- Startup validation of `.claude.json` content\n- Documentation about `.claude.json` vs `.claude/settings.json` gotcha\n- Host-level setup dependency\n\n## Why This Is Also MORE Robust\n- Single mechanism = single point of verification\n- No silent misconfiguration (env var is explicit and logged)\n- No host-level dependency — harness is self-contained\n- If Claude Code ever changes how `.claude.json` works, no impact\n\n## Specs Affected\n- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (primary)\n- `doc/use-case/SpawnTmuxAgentSessionUseCase.md` (env var setup during spawn)
--------------------------------------------------------------------------------

DECISION:
We already moved the auto compaction out to V2 under ./doc_v2/our-own-emergency-compression.md

We shouldnt have disablement problem at all for V1 as we arent disabling for V1 address the docs