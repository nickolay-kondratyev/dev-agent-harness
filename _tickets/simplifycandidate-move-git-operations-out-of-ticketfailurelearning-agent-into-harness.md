---
closed_iso: 2026-03-17T20:50:51Z
id: nid_6wp9jlm7u11cza0qfzr5phuz1_E
title: "SIMPLIFY_CANDIDATE: Move git operations out of TicketFailureLearning agent into harness"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:03:16Z
status_updated_iso: 2026-03-17T20:50:51Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, git, failure-learning]
---

The TicketFailureLearningUseCase (doc/use-case/TicketFailureLearningUseCase.md) spawns a non-interactive agent that performs git operations:
- git commit on the try branch
- git checkout originating branch
- cherry-pick style file propagation (checkout file from try branch)
- git commit on originating branch
- git checkout back to try branch

## Problem
Git operations performed by automated agents are inherently fragile:
- Originating branch may have diverged → checkout fails
- Working tree may be dirty → commit fails
- Agent may misinterpret git error messages
- Race conditions if another process is also doing git operations

The harness already has GitCommitStrategy and git operation infrastructure. Having the agent ALSO do git operations creates two separate git operation paths.

## Proposal
The non-interactive agent should ONLY produce text output (the failure summary). The harness receives the output and handles all git operations:
1. Agent produces failure summary text → returns via stdout (--print mode)
2. Harness appends TRY-N section to ticket file
3. Harness commits on try branch using existing GitCommitStrategy
4. Harness handles best-effort propagation to originating branch

## Benefits
- SIMPLER: Agent has one job (analyze and produce text). Harness has one job (git operations). No overlap.
- MORE ROBUST: Git operations happen through the established harness git infrastructure, not through an agent that might mishandle errors
- Agent instructions become simpler (remove 3 of 5 tasks)
- Eliminates the fragile cherry-pick propagation done by the agent

## Affected Specs
- doc/use-case/TicketFailureLearningUseCase.md (agent instructions, flow)
- doc/core/git.md (potentially add a commit hook point for failure learning)

## Risk
- Low: The agent output format is already structured (TRY-N template). Parsing it in the harness is straightforward. The harness already manages all other git operations.

## Resolution

**Completed** — all specs updated to move git operations from agent to harness.

### Changes made (spec-only):
1. **`doc/use-case/TicketFailureLearningUseCase.md`** — Major rewrite: agent now only reads artifacts and outputs summary text on stdout. Harness handles ticket file mutation, commit on try branch, and best-effort propagation. Agent instructions reduced from 5 tasks to 2. Added new "Agent Output Format" section. Edge cases updated: even if agent fails, harness still records structured facts.
2. **`doc/core/git.md`** — Updated "Git Operations Summary" table: workflow failure row now reflects harness-owned git operations.
3. **`doc/core/TicketShepherd.md`** — Updated `originatingBranch` field: "harness-owned best-effort propagation" instead of "agent instructions for propagation".
4. **`doc/core/TicketShepherdCreator.md`** — Same originating branch description update.
5. **`doc/core/NonInteractiveAgentRunner.md`** — Updated consumers table: purpose changed to "produce failure summary text (stdout only)", timeout reduced to 10 min.

