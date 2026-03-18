---
id: nid_m3t3l7rry2eyye7zn2z72ahs9_E
title: "SIMPLIFY_CANDIDATE: Simplify Q&A to sequential per-question delivery instead of batch"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:47:56Z
status_updated_iso: 2026-03-18T14:47:56Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, qa]
---

The current Q&A mechanism in UserQuestionHandler (doc/core/UserQuestionHandler.md) uses batch delivery: all questions are queued, all answers collected, then delivered together. This adds coordinator lifecycle complexity for a scenario (multiple rapid-fire questions) that is likely rare.

## Current Complexity
- QAPendingState coordinator manages a question queue
- Questions are accumulated, answers are accumulated
- Delivery is deferred until ALL questions in the batch are answered
- Coordinator lifecycle: per-session coroutine, sequential question processing, batch delivery
- isQAPending gate suppresses health pings during the entire batch
- Batch delivery timing coordination (all answers delivered at once via single TMUX send-keys)

## Proposed Simplification
Sequential per-question delivery: when a question arrives, prompt user, deliver answer immediately, loop back to health monitoring. No queue, no batch, no coordinator lifecycle.

Flow: question received -> health pings suppressed -> user prompted -> answer delivered -> health pings resumed -> back to monitoring loop.

## Why This Improves Robustness
- Simpler delivery path (no queue management that could lose questions)
- Agent resumes sooner (no waiting for other questions to be answered)
- No batch timing coordination that could deliver answers out of order
- Health ping suppression is scoped per-question instead of per-batch (narrower suppression window)
- If a question delivery fails, only one question is affected, not the entire batch

## What We Lose (likely acceptable)
- Batching prevents agent from resuming mid-flight while additional answers are pending. But: (a) the multi-question scenario (agent fires 2+ questions before any is answered) is likely rare in practice, (b) if it does happen, sequential delivery means the agent processes answers one at a time anyway, (c) the agent can always ask another question after processing the first answer.

## Specs Affected
- doc/core/UserQuestionHandler.md (primary — simplify to sequential delivery)
- doc/core/SessionsState.md (QAPendingState may be simplified or removed)
- doc/core/PartExecutor.md (health monitoring loop interaction simplified)

