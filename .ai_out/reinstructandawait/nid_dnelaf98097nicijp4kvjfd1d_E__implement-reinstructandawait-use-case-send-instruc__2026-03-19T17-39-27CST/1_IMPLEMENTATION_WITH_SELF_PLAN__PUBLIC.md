# ReInstructAndAwait Implementation - PUBLIC

## What Was Done

Implemented the `ReInstructAndAwait` use case per spec at `doc/use-case/ReInstructAndAwait.md` (ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E).

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwait.kt` | `ReInstructOutcome` sealed class, `ReInstructAndAwait` interface, `ReInstructAndAwaitImpl` class (ap.fXi4IJBxh0ez1Z7tvoamj.E) |
| `app/src/test/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwaitImplTest.kt` | 12 BDD-style unit tests covering all signal mappings |

## Implementation Details

- **ReInstructOutcome**: Sealed class with `Responded(AgentSignal.Done)`, `FailedWorkflow(reason)`, `Crashed(details)`
- **ReInstructAndAwaitImpl**: Constructor-injected with `AgentFacade`. Converts `message` string to `AgentPayload(Path.of(message))`, calls `sendPayloadAndAwaitSignal`, maps `AgentSignal` to `ReInstructOutcome`.
- **SelfCompacted handling**: Treated as `Crashed` since the facade should handle self-compaction transparently -- if it reaches this class, something is wrong.

## Decisions

1. **No `OutFactory` dependency**: The impl is a thin mapper with no logging needs. Removed unused `out` property flagged by detekt. If logging is needed later, `OutFactory` can be added.
2. **SelfCompacted mapped to Crashed**: Per spec, the facade handles self-compaction transparently. If `SelfCompacted` somehow reaches this class, it indicates an unexpected state, so we surface it as a crash rather than silently ignoring.

## Test Results

- 12 tests, 0 failures, 0 skipped
- All tests use `FakeAgentFacade` for isolation
- Scenarios: Done(COMPLETED), Done(PASS), Done(NEEDS_ITERATION), Crashed, FailWorkflow, SelfCompacted (unexpected), payload/handle verification
