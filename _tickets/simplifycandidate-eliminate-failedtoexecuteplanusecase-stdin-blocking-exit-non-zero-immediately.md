---
id: nid_kjwk0b0fote876s4z2rulk514_E
title: "SIMPLIFY_CANDIDATE: Eliminate FailedToExecutePlanUseCase stdin blocking — exit non-zero immediately"
status: open
deps: []
links: []
created_iso: 2026-03-18T02:22:42Z
status_updated_iso: 2026-03-18T02:22:42Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, failure-handling, robustness]
---

## Problem

The FailedToExecutePlanUseCase (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) currently:
1. Prints red error to console
2. Leaves TMUX sessions alive for debugging
3. Blocks on stdin (waits for Enter)
4. On Enter: kills sessions → runs TicketFailureLearningUseCase → exits non-zero

The stdin blocking creates problems:
- Process hangs indefinitely in unattended/CI scenarios
- Couples a CLI tool to interactive terminal availability
- TMUX scrollback is ephemeral anyway (lost on reboot)
- All debug info is already persisted in `.ai_out/` and git history

## Proposed Simplification

On failure:
1. Print red error to console (unchanged)
2. Kill all TMUX sessions immediately
3. Run TicketFailureLearningUseCase (best-effort, unchanged)
4. Exit non-zero

No stdin blocking. No interactive prompt.

## What Gets Removed
- Interactive stdin blocking logic in FailedToExecutePlanUseCase
- "Press Enter to continue" prompt
- The conditional "leave TMUX alive" behavior

## Why This Is Also MORE Robust
- No hanging process in automated/unattended scenarios
- Deterministic behavior — always cleans up, always exits
- Debug artifacts preserved in `.ai_out/` and git history regardless
- Simpler process lifecycle — no blocked-waiting state
- If live TMUX debugging is needed, can be re-added as opt-in `--keep-sessions-on-failure` flag

## Specs Affected
- `doc/use-case/HealthMonitoring.md` (FailedToExecutePlanUseCase detail section)

