# Implementation Review -- Private Notes

## Verification Steps Performed

1. Read all 8 files listed in the review task
2. Ran `sanity_check.sh` -- passed (exit code 0)
3. Verified both test result files show all tests passing (0 failures, 0 skipped)
4. Checked git diff against main to understand full scope of changes
5. Read the original production file on main to confirm the bug was real
6. Verified `ReInstructAndAwait.execute` contract expects file path (not content)
7. Verified `ReInstructAndAwaitImpl` calls `Path.of(message)` confirming the runtime failure
8. Checked for production wiring sites -- none exist yet (feature under development)
9. Verified no existing test scenarios were removed

## Key Analysis Points

### Bug Legitimacy
The bug is 100% real. On `main`, line `val reviewerOutcome = reInstructAndAwait.execute(reviewerHandle, reviewerMessage)` passes the full multi-line reviewer judgment message as the `message` parameter. `ReInstructAndAwaitImpl.execute()` does `Path.of(message)` which would throw `InvalidPathException` for any multi-line string or string containing characters invalid in file paths (like the em-dash in the message template).

This was never caught because:
- The existing `RejectionNegotiationUseCaseImplTest` uses `FakeReInstructAndAwait` which just stores the string -- never calls `Path.of()` on it
- No production wiring exists yet, so no runtime execution path exercises this

### Kotest Isolation Concern (Resolved)
Initially was concerned about shared `FakeAgentFacade` state across `it` blocks, but `DescribeSpec` in Kotest uses `IsolationMode.SingleInstance` by default where the describe block body is a constructor-like scope. Each `it` block runs in the context of the full describe tree, which means the `FakeAgentFacade()` construction in the describe block runs fresh for each leaf test. Confirmed this is not an issue.

### Test Coverage Analysis
All 8 requested scenarios are covered:
1. Reviewer PASS -> Accepted (via full stack)
2. Reviewer NEEDS_ITERATION + doer ADDRESSED -> AddressedAfterInsistence (via full stack)
3. Reviewer COMPLETED (unexpected) -> AgentCrashed (via full stack)
4. Reviewer insists + doer SKIPPED -> AgentCrashed (via full stack)
5. Reviewer insists + doer invalid marker -> AgentCrashed (via full stack)
6. Reviewer Crashed signal -> AgentCrashed propagation (via full stack)
7. Reviewer FailWorkflow signal -> FailedWorkflow propagation (via full stack)
8. Interaction recording verification (sendPayloadCalls count and ordering)

### What the wired tests add over existing tests
The existing `RejectionNegotiationUseCaseImplTest` fakes at the `ReInstructAndAwait` boundary. The new tests fake at the `AgentFacade` boundary, exercising:
- `ReInstructAndAwaitImpl`'s signal mapping logic
- `AgentPayload` construction with `Path.of(message)`
- The full serialization/deserialization of signals through the real `ReInstructAndAwaitImpl`

This is valuable because it would have caught the message-vs-path bug if it had existed when these tests were written.
