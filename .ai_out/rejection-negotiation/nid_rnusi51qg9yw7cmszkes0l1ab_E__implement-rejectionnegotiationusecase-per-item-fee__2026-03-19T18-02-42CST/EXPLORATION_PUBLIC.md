# Exploration: RejectionNegotiationUseCase

## Spec
- `doc/plan/granular-feedback-loop.md` (ap.5Y5s8gqykzGN1TVK5MZdS.E) — sections REJECTION_NEGOTIATION (lines 329-352), R5 (lines 519-538), Gate 4 (lines 630-642)

## Dependencies (both CLOSED)
- `ReInstructAndAwait` at `app/src/main/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwait.kt`
  - `fun interface` with `execute(handle, message) -> ReInstructOutcome`
  - `ReInstructOutcome`: `Responded(signal: AgentSignal.Done)`, `FailedWorkflow(reason)`, `Crashed(details)`
- `FeedbackResolutionParser` at `app/src/main/kotlin/com/glassthought/shepherd/feedback/FeedbackResolutionParser.kt`
  - `object` with `parse(fileContent) -> ParseResult`
  - `ParseResult`: `Found(resolution)`, `MissingMarker`, `InvalidMarker(rawValue)`
  - `FeedbackResolution`: `ADDRESSED`, `REJECTED`, `SKIPPED`

## Key Types
- `SpawnedAgentHandle` at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnedAgentHandle.kt` — has `guid`, `sessionId`, `lastActivityTimestamp`
- `AgentSignal.Done(result: DoneResult)` — `DoneResult`: `COMPLETED`, `PASS`, `NEEDS_ITERATION`
- `AgentSignal.FailWorkflow(reason)`, `AgentSignal.Crashed(details)`

## Flow (from spec)
1. Doer writes `## Resolution: REJECTED` → harness sends rejection+reasoning to reviewer via `ReInstructAndAwait`
2. Reviewer signals `done pass` (accept) or `done needs_iteration` (insist)
3. If pass → `RejectionResult.Accepted`
4. If insist → re-instruct doer via `ReInstructAndAwait` with counter-reasoning
5. Doer writes `## Resolution: ADDRESSED` → `RejectionResult.AddressedAfterInsistence`
6. Doer still REJECTED → `RejectionResult.AgentCrashed`
7. Any crash/fail-workflow → map to appropriate RejectionResult variant

## Testing Pattern
- `FakeAgentFacade` at `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt`
  - Programmable: `onSendPayloadAndAwaitSignal { handle, payload -> AgentSignal.Done(...) }`
  - Records calls for verification
- Extend `AsgardDescribeSpec`, BDD GIVEN/WHEN/THEN, one assert per `it` block

## Existing Use Case Patterns
- `FailedToConvergeUseCase` — fun interface + Impl, constructor injection
- `ReInstructAndAwaitImpl` — similar pattern

## Package
`com.glassthought.shepherd.usecase`

## Note
RejectionNegotiationUseCase owns its own message templates (inline, not via ContextForAgentProvider).
