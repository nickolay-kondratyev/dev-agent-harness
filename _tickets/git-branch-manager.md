---
id: nid_jwu5k36fgwnf4unw4x93sgpy6_E
title: "Git Branch Manager"
status: open
deps: [nid_r9on08uqjmumuc6wi2c53e8p9_E]
links: []
created_iso: 2026-03-09T23:06:52Z
status_updated_iso: 2026-03-09T23:06:52Z
type: feature
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [wave1, git]
---

Implement branch name building from ticket data and git branch creation/checkout.

## Scope
- Create `BranchNameBuilder`: builds `{TICKET_ID}__{slugified_title}__try-{N}` from ticket data
  - Slugify: lowercase, spaces/special chars → hyphens, collapse consecutive hyphens, trim
  - Truncate slug if too long (max ~60 chars for the slug portion)
  - `__` (double underscore) as delimiter between components
  - `try-N` starts at 1
- Create `GitBranchManager` interface + implementation:
  - `createAndCheckout(branchName: String)` — creates branch and checks it out
  - `getCurrentBranch(): String`
  - Uses `ProcessRunner` from asgardCore for git commands
- Package: `com.glassthought.chainsaw.core.git`

## Dependencies
- Uses `TicketData` data class from Ticket Parser ticket (nid_r9on08uqjmumuc6wi2c53e8p9_E)
- Uses `ProcessRunner` from asgardCore (already available)

## Key Decisions
- BranchNameBuilder is a pure function (no side effects) — easy to test
- GitBranchManager wraps git CLI commands via ProcessRunner
- Fail-fast if git command fails (non-zero exit code)
- V1: no DirectLLMApi compression of long titles — just truncate. LLM compression can be added later.

## Testing
- Unit tests for BranchNameBuilder:
  - Test: simple title slugifies correctly
  - Test: title with special characters slugifies correctly
  - Test: long title gets truncated
  - Test: try number is appended correctly
  - Test: full format `{id}__{slug}__try-{N}` is correct
- Integration tests for GitBranchManager (gated with isIntegTestEnabled):
  - Test: creates and checks out branch in a temp git repo

## Files touched
- New files under `app/src/main/kotlin/com/glassthought/chainsaw/core/git/`
- New files under `app/src/test/kotlin/com/glassthought/chainsaw/core/git/`
- Does NOT touch `app/build.gradle.kts`

## Reference
- See "Git Branch / Feature Naming" section in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`

## Completion Criteria — Anchor Point
As part of closing this ticket:
1. Run `anchor_point.create` to generate a new AP for this component.
2. Add `ap.XXX.E` just below the `## Git Branch / Feature Naming` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`.
3. Add `ref.ap.XXX.E` in the KDoc of the `BranchNameBuilder` class pointing back to that design ticket section.

