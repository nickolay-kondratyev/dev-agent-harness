---
id: nid_hxs92i3pryren8vhn71ji70ck_E
title: "SIMPLIFY_CANDIDATE: Replace FailedToConverge interactive prompt with immediate failure"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:58:46Z
status_updated_iso: 2026-03-18T14:58:46Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, convergence, v1-scope]
---

## Problem
FailedToConvergeUseCase (doc/use-case/HealthMonitoring.md, ref.ap.RJWVLgUGjO5zAwupNLhA0.E) prompts the user interactively via stdin when the iteration budget is exhausted: binary y/N to extend by 2 iterations.

This adds:
- Interactive stdin prompt during automated execution
- Stdin contention with StdinUserQuestionHandler (noted in TicketShepherd interrupt protocol)
- Edge case: user walks away during prompt → harness hangs indefinitely
- Fixed increment of 2 logic
- Resumed iteration budget tracking

## Proposal
When iteration budget is exhausted → immediate failure via FailedToExecutePlanUseCase.

The user can restart with --iteration-max increased. Cross-try learning (even structured-facts-only) captures the context. The new try picks up where things left off conceptually.

## Why More Robust
- More predictable: exhausted budget = failure, always
- No hanging state waiting for stdin input
- No stdin contention with UserQuestionHandler
- No edge case of unattended prompt
- Simpler PartExecutor control flow (no mid-execution budget extension)
- Aligns with fail-fast philosophy

## What Gets Eliminated
- FailedToConvergeUseCase interactive prompt logic
- Budget extension tracking in PartExecutor
- Stdin contention handling between convergence prompt and Q&A
- Fixed increment logic

## Trade-off
- User must restart workflow to increase budget (minor: takes ~30 seconds)
- Cross-try learning captures context, so no information is lost
- User explicitly chose the budget via --iteration-max; respecting that choice is principled

## Affected Specs
- doc/use-case/HealthMonitoring.md (simplify FailedToConvergeUseCase to immediate failure)
- doc/core/PartExecutor.md (remove budget extension path)
- doc/core/TicketShepherd.md (simplify PartResult handling for FailedToConverge)

