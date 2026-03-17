---
closed_iso: 2026-03-17T17:37:06Z
id: nid_etfsnuwx7spgvh8ayj0z12deg_E
title: "SIMPLIFY_CANDIDATE: Consolidate GitCommitStrategy hooks — single onPartDone instead of onSubPartDone + onPartDone"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:31:32Z
status_updated_iso: 2026-03-17T17:37:06Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, git, commit-strategy]
---

FEEDBACK:
--------------------------------------------------------------------------------
## Current State
Git spec (doc/core/git.md) defines GitCommitStrategy with two hooks:
- onSubPartDone: commits after each sub-part completes
- onPartDone: commits after the entire part completes

This means:
- Two commit points per part (for DoerReviewer: after doer, after reviewer, after part)
- More complex strategy interface with two methods
- The commit message convention must distinguish sub-part vs part commits
- More commits in the git history to reason about

## Proposed Simplification
Single hook: onPartDone. Commits only at part boundaries. Remove onSubPartDone entirely.

## Why This Improves Robustness
- Simpler strategy interface (1 method instead of 2)
- Fewer commits = cleaner git history = easier to reason about
- Sub-part work is still captured — it is just committed as one atomic unit per part
- Reduces the window for git operation failures (fewer git operations = fewer failure opportunities)
- If a sub-part fails mid-way, the uncommitted state is actually desirable — the next try starts clean

## Trade-off
- Loss of granularity: you cannot git-revert a single sub-part independently. For V1 this is acceptable because parts are small (max 2 sub-parts) and the try-N mechanism already provides full-part retry.

## Spec references
- doc/core/git.md (GitCommitStrategy interface, commit message convention)

DECISION
--------------------------------------------------------------------------------
## Decision: keep onSubPartDone
KEEP onSubPartDone

The reason why is that early ON (starting strategy) we will want to commit on each onSubPartDone so that we can audit the communication between the agents. As we are starting with PUBLIC.md being overridden.

However, once we get going we could switch to just onPartDone. 

SO to KISS for V1 lets have onSubPartDone implementation WITHOUT onPartDone yet. And commit per onSubPartDone


