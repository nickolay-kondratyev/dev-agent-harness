---
id: nid_o9z2nk2kwqrxwa6g12rlyrihr_E
title: "SIMPLIFY_CANDIDATE: Defer TicketFailureLearning agent summary to V2 — use structured facts template only"
status: open
deps: []
links: [nid_bmf7mq2lrusqiibmubwtrvyy0_E]
created_iso: 2026-03-18T14:58:40Z
status_updated_iso: 2026-03-18T14:58:40Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, failure-learning, v1-scope]
---

## Problem
TicketFailureLearningUseCase (doc/use-case/TicketFailureLearningUseCase.md, ref.ap.cI3odkAZACqDst82HtxKa.E) spawns a ClaudeCode --print agent (sonnet, 20-min timeout) to generate a summary with Approach/Root Cause/Recommendations. This adds:
- ClaudeCode --print subprocess management
- 20-min timeout during an already-failed workflow
- Agent instruction assembly for failure context
- Handling of agent failure within failure handling (what if learning agent crashes?)
- Best-effort propagation to originating branch

The harness already assembles structured facts (failureType, failedAt, iteration, partsCompleted) deterministically.

## Proposal
In V1, write ONLY the structured facts to the ticket TRY-N section. Skip the agent-generated Approach/Root Cause/Recommendations.

The structured facts already contain:
- Workflow type
- Failure type (AgentCrashed / FailedToConverge / FailedWorkflow)
- Where it failed (part name, sub-part)
- Iteration count
- Parts completed vs remaining
- Branch name and try number

This is sufficient for cross-try learning. A human or future agent can derive root cause from these facts + the git diff.

## Why More Robust
- Deterministic template CANNOT fail — it is a simple string format
- Removes a failure point during failure handling (agent-based summary in an already-failed workflow)
- No 20-min wait when the user is already dealing with a failure
- Instant feedback: structured facts written immediately

## What Gets Eliminated
- Agent-based summary generation in TicketFailureLearningUseCase
- ClaudeCode --print agent invocation for learning
- Learning-specific instruction template
- Agent failure handling within learning
- If combined with AutoRecovery deferral: entire NonInteractiveAgentRunner can be eliminated

## Affected Specs
- doc/use-case/TicketFailureLearningUseCase.md (simplify to template-only)
- doc/core/NonInteractiveAgentRunner.md (remove ClaudeCode consumer; may eliminate entirely if AutoRecovery also deferred)

