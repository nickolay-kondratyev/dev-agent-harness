# RejectionNegotiationUseCase Implementation

## What Was Done

Implemented `RejectionNegotiationUseCase` (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) — a self-contained
sub use case for per-item rejection negotiation between doer and reviewer agents.

### Sealed Result Type
- `RejectionResult.Accepted` — reviewer accepts the doer's rejection
- `RejectionResult.AddressedAfterInsistence` — reviewer insisted, doer complied
- `RejectionResult.AgentCrashed` — agent crashed or doer defied authority
- `RejectionResult.FailedWorkflow` — agent signaled fail-workflow

### Flow
1. Read feedback file content (doer's rejection reasoning)
2. Send to reviewer via `ReInstructAndAwait` asking for judgment
3. Reviewer signals `done pass` (accept) or `done needs_iteration` (insist)
4. If insist: re-instruct doer to comply, validate updated resolution marker

### Design Decisions
- **FeedbackFileReader interface**: Extracted for testability — avoids real filesystem in unit tests
- **FakeReInstructAndAwait**: Test helper dispatches by handle identity, allowing separate reviewer/doer responses
- **Message templates owned inline**: Per spec, not routed through ContextForAgentProvider
- **FeedbackResolutionParser used as object**: No constructor injection needed (it's stateless)
- **COMPLETED signal from reviewer**: Treated as AgentCrashed (unexpected during negotiation)
- **Anchor point**: ap.cGkhniuHpDBfYmBQH36ea.E

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/.../usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt` | Interface, sealed result class, and implementation |
| `app/src/main/kotlin/.../usecase/rejectionnegotiation/FeedbackFileReader.kt` | File reader interface for testability |
| `app/src/test/kotlin/.../usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt` | 22 unit tests covering all scenarios |

## Test Coverage (22 tests, all passing)
1. REJECTED -> reviewer accepts (PASS) -> `Accepted`
2. REJECTED -> reviewer insists (NEEDS_ITERATION) -> doer addresses -> `AddressedAfterInsistence`
3. REJECTED -> reviewer insists -> doer still rejects -> `AgentCrashed`
4. Reviewer crashes during judgment -> `AgentCrashed`
5. Doer crashes during compliance -> `AgentCrashed`
6. Reviewer signals fail-workflow -> `FailedWorkflow`
7. Doer signals fail-workflow during compliance -> `FailedWorkflow`
8. Missing resolution marker after compliance -> `AgentCrashed`
9. Message template content verification (reviewer judgment + doer compliance)
