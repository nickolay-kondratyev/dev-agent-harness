---
id: nid_5higjz0yuy18qffhm4eiisjio_E
title: "SIMPLIFY_CANDIDATE: Remove per-sub-part noActivityTimeout override — use single global timeout"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:31:22Z
status_updated_iso: 2026-03-15T01:31:22Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, health-monitoring, config]
---

## Current State
HealthMonitoring spec (doc/use-case/HealthMonitoring.md) allows per-sub-part noActivityTimeout overrides in the SubPart schema (doc/schema/plan-and-current-state.md). This means:
- The SubPart JSON schema includes an optional timeout field
- The health monitoring loop must check per-sub-part config before using the global default
- Plan authors (agents or humans) must decide timeout values per sub-part
- The configuration surface area grows with each new sub-part concern

## Proposed Simplification
Use a single global noActivityTimeout (default: 30 minutes) for all sub-parts. Remove the per-sub-part override from the SubPart schema.

## Why This Improves Robustness
- Eliminates a configuration concern from the SubPart schema (simpler JSON, simpler parsing)
- Removes conditional logic in the health monitoring loop
- Prevents misconfigured timeouts (e.g., a plan sets 1 minute, causing false crashes)
- One timeout value to tune globally rather than per-sub-part
- If a specific task genuinely needs longer, the global can be bumped — the 80/20 solution

## Spec references
- doc/use-case/HealthMonitoring.md (per-sub-part noActivityTimeout override)
- doc/schema/plan-and-current-state.md (SubPart fields table)

