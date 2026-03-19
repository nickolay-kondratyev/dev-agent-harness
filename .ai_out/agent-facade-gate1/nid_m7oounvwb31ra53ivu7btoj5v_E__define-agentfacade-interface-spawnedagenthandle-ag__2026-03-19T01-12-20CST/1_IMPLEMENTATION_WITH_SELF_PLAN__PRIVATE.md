# Gate 1 — PRIVATE Implementation Notes

## Status: COMPLETE

## What Was Done
Created 6 files in `com.glassthought.shepherd.core.agent.facade` package — interfaces and data classes only (no implementations).

## Anchor Points Assigned
| Type | Anchor Point |
|------|-------------|
| AgentFacade (interface) | ap.1aEIkOGUeTijwvrACf3Ga.E |
| AgentSignal (sealed class) | ap.uPdI6LlYO56c1kB5W0dpE.E |
| ContextWindowState | ap.f4OVHiR0b7dpozBJDmIhv.E |
| SpawnAgentConfig | ap.nDDZyl11vax5mqhyAiiDr.E |
| AgentPayload | ap.dPr77qbXaTmUgH0R3OBq0.E |
| SpawnedAgentHandle | ap.kWchUPtTLqu73qXLHbKMs.E |

## Decisions Made
1. **`@Volatile var` for lastActivityTimestamp** — `kotlinx.atomicfu` is not a project dependency. `@Volatile var` is sufficient for single-writer patterns (facade updates, test controls).
2. **`data class` for SpawnedAgentHandle** — Even though it has a mutable field (`lastActivityTimestamp`), it is a data class because `guid` and `sessionId` are the identity fields. The mutable timestamp is observable state, not identity. `equals`/`hashCode` will include the timestamp, which is acceptable since handles are compared by reference in practice.
3. **No `AgentSpawnException`** created yet — referenced in KDoc for `spawnAgent` but not defined. This is intentional for Gate 1 (interfaces only); the exception class will be created when `AgentFacadeImpl` is implemented.

## Build Verification
- `./gradlew :app:build` passes with exit code 0.
