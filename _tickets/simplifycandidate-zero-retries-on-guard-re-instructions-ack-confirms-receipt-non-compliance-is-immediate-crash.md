---
id: nid_1h1l9zjbzun9qc7aq67dt9550_E
title: "SIMPLIFY_CANDIDATE: Zero retries on guard re-instructions — ACK confirms receipt, non-compliance is immediate crash"
status: open
deps: []
links: []
created_iso: 2026-03-18T00:08:13Z
status_updated_iso: 2026-03-18T00:08:13Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, spec-change]
---

## Current Design (ref.ap.THDW9SHzs1x2JN9YP9OYU.E, ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E)

Every guard point in PartExecutor uses a "one retry" pattern via `ReInstructAndAwait`:
- PUBLIC.md missing after done → re-instruct, one retry, then AgentCrashed
- PRIVATE.md missing after self-compaction → re-instruct, one retry, then AgentCrashed
- Feedback files missing after needs_iteration → re-instruct, one retry, then AgentCrashed
- Resolution marker missing on feedback file → re-instruct, one retry, then AgentCrashed
- Part completion guard (critical/important in pending) → re-instruct, one retry, then AgentCrashed

That is 5+ guard locations, each with identical retry logic.

## Problem

The retry is predicated on "maybe the agent didn't receive the instruction.\" But this is already solved by the **Payload Delivery ACK protocol** (ref.ap.tbtBcVN2iCl1xfHJthllP.E):
- Every instruction sent via `send-keys` is wrapped in ACK XML
- Agent must `ack-payload <ID>` before processing
- 3-minute timeout per attempt, 3 attempts max
- By the time `ReInstructAndAwait` returns, the harness KNOWS the agent received and acknowledged the instruction

If the agent received the instruction (ACK confirmed), processed it, signaled done, but still didn't produce the expected output — the agent is fundamentally confused. One more retry won't fix confusion.

## Proposed Simplification

Remove the retry from all guard points. If guard check fails after ACK-confirmed instruction delivery → immediate `PartResult.AgentCrashed`.

```
// Before (at each guard point):
check() → fail → ReInstructAndAwait (one retry) → check() → fail → AgentCrashed

// After:
check() → fail → AgentCrashed
```

The `ReInstructAndAwait` use-case class can be simplified or removed entirely:
- Guard checks happen AFTER the agent already signaled `done`
- The agent had explicit instructions about what to produce
- ACK confirmed receipt
- Non-compliance after all that = broken agent

## Why This Is Both Simpler AND More Robust

- Eliminates retry logic at 5+ guard locations
- Faster failure detection (no 3+ minute retry wait)
- Removes `ReInstructAndAwait` as a dependency of `PartExecutor` (or simplifies it significantly)
- More honest: retry was masking the real question — \"did the agent receive it?\" — which ACK already answers
- No behavioral regression for well-functioning agents (they produce expected output on first attempt)
- Reduces total guard point code by ~50% (check + fail vs check + retry + recheck + fail)

## Risk Assessment

Minimal. The retry catches a narrow case: agent received instruction, acknowledged it, but \"forgot\" during processing. With the ACK protocol, this is agent confusion, not delivery failure. Retrying confused agents wastes time.

## Spec Files to Update

- `doc/use-case/ReInstructAndAwait.md` (simplify or remove)
- `doc/core/PartExecutor.md` (all guard point descriptions — PUBLIC.md, feedback files, resolution marker, part completion)
- `doc/plan/granular-feedback-loop.md` (guard re-instruction references)
- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (PRIVATE.md guard reference)

