# Gate 6: Granular Feedback Loop Integration Test

## What Was Done

Created a wired integration test (`GranularFeedbackLoopIntegTest`) that validates all granular feedback loop components work together end-to-end. The test wires REAL instances of:
- `InnerFeedbackLoop` — per-item feedback processing with severity ordering
- `RejectionNegotiationUseCaseImpl` — full rejection negotiation flow
- `PartCompletionGuard` — blocks completion with unresolved critical/important
- `FeedbackResolutionParser` — parses `## Resolution:` markers
- `PublicMdValidator` — validates PUBLIC.md exists

Only agent communication boundaries are faked (`ReInstructAndAwait`, `FeedbackFileReader`, `FakeAgentFacade`, `GitCommitStrategy`).

## Test Scenarios Covered

1. **Happy path (ADDRESSED)**: Reviewer writes critical + important + optional feedback files. Doer addresses each. All files move to `addressed/` in severity order. Git commits recorded. Iteration counter stays constant.

2. **Rejection accepted**: Doer writes `REJECTED`. Reviewer signals `PASS` (accepts). File moves to `rejected/`.

3. **Rejection insistence with compliance**: Doer writes `REJECTED`. Reviewer signals `NEEDS_ITERATION` (insists). Doer complies with `ADDRESSED`. File moves to `addressed/`.

4. **Part completion guard**: Tests that `PartCompletionGuard` fails when `critical__*` remains in `pending/`, passes when only `optional__*` remains (moves to `addressed/`), and passes when `pending/` is empty.

5. **Iteration counter semantics**: Verified via Scenario 1 — multiple items processed in inner loop, all git commits have same iteration number (counter does NOT increment per item).

6. **Feedback files presence guard**: Empty `pending/` directory triggers `Terminate(AgentCrashed)`.

7. **Optional SKIPPED handling**: Doer writes `SKIPPED` on optional item — moves to `addressed/`. Doer writes `SKIPPED` on critical item — triggers `AgentCrashed`.

8. **Mixed flow (bonus)**: Critical (ADDRESSED) + important (REJECTED+accepted) + optional (SKIPPED) all in one inner loop invocation. Validates correct file placement across all three output directories.

## Files Modified

- **Created**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/feedback/GranularFeedbackLoopIntegTest.kt`

## Key Design Decisions

- **Extends `AsgardDescribeSpec`** (not `SharedContextDescribeSpec`) since we use `FakeAgentFacade`, not real agents. No `isIntegTestEnabled()` gate needed — runs as a regular unit test.
- **`QueueBasedFeedbackFileReader`**: Custom test helper that supports ordered reads per path. Each file is read multiple times (original content for instruction assembly, then resolved content for resolution parsing), so queue-based semantics map naturally to the multi-read pattern.
- **Single `ReInstructAndAwait` lambda with call counter**: Simulates the sequence of agent interactions (doer processes, reviewer judges, doer complies) in the correct order.

## Test Results

All tests pass. Full suite (`./test.sh`) green.
