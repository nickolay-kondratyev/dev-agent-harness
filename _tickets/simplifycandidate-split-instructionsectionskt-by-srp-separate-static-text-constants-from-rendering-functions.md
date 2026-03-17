---
id: nid_fpi6tyz0ab5i3ll3ka96l7mbr_E
title: "SIMPLIFY_CANDIDATE: Split InstructionSections.kt by SRP — separate static text constants from rendering functions"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T22:47:14Z
status_updated_iso: 2026-03-17T23:05:29Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, srp, context, maintainability]
---

## Problem
`app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSections.kt` is 410+ lines and mixes three distinct responsibilities in one file:

1. **Static instruction text** — 14+ `val` constants with multiline Markdown strings (e.g., `CLAUDE_CODE_BOOTSTRAP_SECTION`, `CALLBACK_HELP_SECTION`, `ROLE_DEFINITIONS`)
2. **Rendering functions** — dynamic renderers that format data into instruction strings (e.g., role catalog rendering, feedback formatting)
3. **Data class** — `RoleCatalogEntry` defined inline

This violates SRP: reasons to change include updating static copy, adding a new rendering function, and changing the `RoleCatalogEntry` schema — three distinct reasons.

## Proposed Simplification
Split into focused files:
- `InstructionText.kt` — static `object` with all text constants (simple, rarely changes)
- `InstructionRenderers.kt` — functions that produce formatted strings from data
- Move `RoleCatalogEntry` to its own file or into `ContextForAgentProvider.kt` where it's used

## Why This Improves Both
- **Simpler**: Each file has a single clear purpose, easier to navigate
- **More robust**: Changes to static copy don't risk accidentally modifying rendering logic and vice versa
- **Faster to review**: Smaller, focused files are easier to diff and code-review

## Acceptance Criteria

- InstructionSections.kt split into at least 2 focused files
- Static text constants separated from rendering functions
- All instruction assembly tests pass
- No behavior change in assembled instructions
- Make sure any inputs into the strings are clearly stated, such as with input arguments that are templatized.

