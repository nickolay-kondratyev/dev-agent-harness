---
id: nid_dhkir4fqzy8tcgmh7nyj23hzp_E
title: "SIMPLIFY_CANDIDATE: Remove ContextWindowStateReader.validatePresence() — let read() fail naturally on first health check"
status: open
deps: []
links: []
created_iso: 2026-03-18T02:23:33Z
status_updated_iso: 2026-03-18T02:23:33Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, context-window, robustness]
---


FEEDBACK
--------------------------------------------------------------------------------
## Problem

The ContextWindowStateReader interface (ref.ap.ufavF1Ztk6vm74dLAgANY.E) has two methods:
1. `validatePresence()` — called after session ID resolution to confirm the context window hook is active
2. `read(agentSessionId)` → `ContextWindowState` — called during health monitoring

`read()` already throws `ContextWindowStateUnavailableException` if the file is missing.
`validatePresence()` catches the same condition but earlier (at spawn time vs first health check).

The early detection adds:
- An extra method in the interface
- An extra call in the spawn flow
- An extra test path
- Marginal time savings (seconds, not minutes — first health check runs shortly after spawn)

## Proposed Simplification

Remove `validatePresence()` from the interface:
- `read()` already throws `ContextWindowStateUnavailableException` for missing files → same hard-fail behavior
- First `read()` call happens within seconds of spawn (during first health check cycle)
- The delay between spawn and first health check is negligible
- One method interface = simpler contract, easier to implement and test

## What Gets Removed
- `validatePresence()` method from `ContextWindowStateReader` interface
- `validatePresence()` call in spawn flow
- Tests for `validatePresence()` behavior

## Why This Is Also MORE Robust
- Single method interface = single behavior to implement correctly
- No divergence risk between what `validatePresence()` checks and what `read()` requires
- Same failure outcome (hard stop with clear error) just milliseconds later
- Simpler interface = fewer ways for implementations to get it wrong

## Trade-off Acknowledged
- If spawn succeeds but context window hook is misconfigured, detection moves from spawn-time to first-health-check-time (seconds later). This is acceptable because:
  - The failure behavior is identical (hard stop)
  - The error message can be equally informative
  - The time difference is negligible in practice

## Specs Affected
- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (validatePresence() reference)
- `doc/use-case/SpawnTmuxAgentSessionUseCase.md` (spawn flow steps)
--------------------------------------------------------------------------------

AS long as first health check runs quite close we can SIMPLIFY.