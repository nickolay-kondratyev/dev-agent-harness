---
id: nid_vaqvylypsr1xoz8j03fhkut3a_E
title: "SIMPLIFY_CANDIDATE: Simplify FailedToConvergeUseCase to fixed iteration increment — y/N instead of variable input"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T23:40:52Z
status_updated_iso: 2026-03-18T14:09:54Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec, health-monitoring]
---

## Problem

FailedToConvergeUseCase (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) currently presents the user with raw reviewer + doer PUBLIC.md and asks them to specify how many additional iterations to grant. This requires:
- Displaying two full PUBLIC.md outputs to stdout
- Parsing user-specified iteration count from stdin
- Validating the count (positive integer)
- Bumping `iteration.max` by the user-specified amount

## Simplification

Replace variable iteration grant with a fixed increment and a simple y/N prompt:

```
Iteration budget exhausted (3/3). Grant 2 more iterations? [y/N]
```

- `y` → `iteration.max += 2`, continue doer→reviewer loop
- `N` or timeout → executor returns `PartResult.FailedToConverge` → `FailedToExecutePlanUseCase`

The user can always re-run the workflow if 2 more iterations aren't enough.\n\n### What this eliminates\n- Variable input parsing (no \"how many?\" prompt, no integer validation)\n- The fixed increment (2) is a reasonable default for most cases\n- Simpler interaction reduces cognitive load on the operator during a failure scenario\n\n### What it preserves\n- User still decides whether to continue or abort (y/N)\n- Raw PUBLIC.md files can still be displayed as context\n- The fixed increment value (2) can be a constant in `HarnessTimeoutConfig` for easy tuning\n\n## Why This Improves Robustness\n\n- Eliminates input parsing edge cases (non-numeric input, negative numbers, zero)\n- Faster decision for the operator — binary choice under pressure is easier than open-ended input\n- Fixed increment prevents \"user types 999\" and burning resources on a fundamentally broken part\n\n## Affected Specs\n\n- `doc/use-case/HealthMonitoring.md` — FailedToConvergeUseCase Detail section\n- `doc/core/PartExecutor.md` — step 4 budget check

