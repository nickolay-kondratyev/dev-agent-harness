---
id: nid_3nuvvjenh4oa5g37m7tx09vj5_E
title: "SIMPLIFY_CANDIDATE: Consolidate AgentStarter + AgentSessionIdResolver into single AgentTypeAdapter interface"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T23:41:20Z
status_updated_iso: 2026-03-17T23:45:29Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec, agent-facade]
---

## Problem

Each agent type (ClaudeCode, PI, future) requires TWO interface implementations:
- `AgentStarter` (ref.ap.RK7bWx3vN8qLfYtJ5dZmQ.E) — builds the shell command to start the agent
- `AgentSessionIdResolver` (ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E) — resolves the session ID from external artifacts

These are always deployed as a pair per agent type. `SpawnTmuxAgentSessionUseCase` dispatches to both based on `agentType`. `AgentFacadeImpl` constructor needs both as dependencies. No caller ever uses one without the other.

The spec itself says: "The pair of AgentStarter + AgentSessionIdResolver per agent type forms the complete agent-type-specific contract." — ref `doc/use-case/SpawnTmuxAgentSessionUseCase.md`

## Simplification

Combine into a single `AgentTypeAdapter` interface:

```kotlin
interface AgentTypeAdapter {
    fun buildStartCommand(bootstrapMessage: String): TmuxStartCommand
    suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String
}
```

- V1: `ClaudeCodeAdapter` implements both methods (builds claude command + scans JSONL)
- Future: `PIAdapter` implements both for PI-specific behavior

### What this eliminates
- One interface definition (2 interfaces → 1)
- One constructor parameter on `AgentFacadeImpl` (2 deps → 1)
- Dual dispatch in `SpawnTmuxAgentSessionUseCase` (dispatch once, not twice)
- The risk of wiring mismatched starter/resolver pairs (ClaudeCode starter + PI resolver)

### What it preserves
- OCP: new agent types add a new `AgentTypeAdapter` implementation
- Testability: tests inject a fake adapter
- All existing behavior

## Why This Improves Robustness

- Impossible to mismatch starter/resolver pairs — they're in the same object\n- Single dispatch point instead of two — fewer opportunities for agent type confusion\n- Simpler wiring in `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)\n\n## ISP Consideration\n\nISP would argue for separate interfaces. But ISP applies when different callers need different subsets. Here, every caller needs both methods together in the spawn flow. ISP-purity at the cost of coupling prevention is not a good trade.\n\n## Affected Specs\n\n- `doc/use-case/SpawnTmuxAgentSessionUseCase.md` — AgentStarter and AgentSessionIdResolver sections\n- `doc/core/AgentInteraction.md` — interface shape, internal delegation table\n- `doc/core/TicketShepherdCreator.md` — wiring section\n- `doc/high-level.md` — Key Technology Decisions table (Agent start command, Session tracking)

