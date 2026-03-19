---
closed_iso: 2026-03-19T17:53:19Z
id: nid_dnelaf98097nicijp4kvjfd1d_E
title: "Implement ReInstructAndAwait use case — send instruction to existing session and await signal"
status: closed
deps: [nid_m7oounvwb31ra53ivu7btoj5v_E, nid_g3e2bkvqepf2wzipadayt9in8_E]
links: []
created_iso: 2026-03-18T22:30:11Z
status_updated_iso: 2026-03-19T17:53:19Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [granular-feedback-loop, use-case]
---

Implement the `ReInstructAndAwait` use case that encapsulates the "send instruction to existing agent session, await next signal" pattern.

## Context
Spec: `doc/use-case/ReInstructAndAwait.md` (ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E)
Used by: granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) and RejectionNegotiationUseCase (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E).

## Problem
The pattern "deliver instruction to already-alive agent, await signal, map AgentSignal to outcome" appears in 3+ call sites in the inner feedback loop and rejection negotiation. Each site would hand-roll ~15-20 lines of identical plumbing.

## Requirements
- Interface with `execute(handle: SpawnedAgentHandle, message: String): ReInstructOutcome`
- Internally delegates to `agentFacade.sendPayloadAndAwaitSignal(handle, message)`
- Maps `AgentSignal` to `ReInstructOutcome` sealed class:
  - `AgentSignal.Done` → `ReInstructOutcome.Responded(signal)`
  - `AgentSignal.FailWorkflow` → `ReInstructOutcome.FailedWorkflow(reason)`
  - `AgentSignal.Crashed` → `ReInstructOutcome.Crashed(details)`
  - `AgentSignal.SelfCompacted` → handled inside facade (transparent to caller)
- Guard checks (PUBLIC.md missing, resolution marker missing, etc.) result in immediate `PartResult.AgentCrashed` — NOT handled by this class

## Testing
- Unit test via `FakeAgentFacade`: agent responds with done → `Responded`
- Unit test: agent crashes → `Crashed`
- Unit test: agent signals fail-workflow → `FailedWorkflow`

## Package
`com.glassthought.shepherd.usecase`

## Spec Reference
Full spec at `doc/use-case/ReInstructAndAwait.md`

## Acceptance Criteria
- All unit tests pass
- Clean DRY abstraction for re-instruction pattern
- Independently testable from PartExecutorImpl


## Notes

**2026-03-19T00:51:21Z**

Added missing deps: AgentFacade interface (nid_m7oounvwb31ra53ivu7btoj5v_E) for compilation and FakeAgentFacade (nid_g3e2bkvqepf2wzipadayt9in8_E) for unit testing. Was incorrectly listed as ready with zero deps.
