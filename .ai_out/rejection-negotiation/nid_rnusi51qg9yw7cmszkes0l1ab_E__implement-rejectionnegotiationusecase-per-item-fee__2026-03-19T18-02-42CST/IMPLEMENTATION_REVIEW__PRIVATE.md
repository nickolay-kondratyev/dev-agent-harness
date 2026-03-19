# Implementation Review: RejectionNegotiationUseCase (Private Notes)

## Verdict: PASS (with IMPORTANT issues)

## Key Findings

### IMPORTANT: Missing reviewer counter-reasoning in doer compliance message

Spec line 346-348 says:
```
message: "Reviewer insists this must be addressed. Their reasoning:
          <reviewer's counter-reasoning from PUBLIC.md>.
          You MUST address this item. Write '## Resolution: ADDRESSED'."
```

Current `buildDoerComplianceMessage` does NOT include reviewer's counter-reasoning. This is likely a design gap rather than a bug -- `ReInstructOutcome.Responded` only carries `AgentSignal.Done(result)`, which has no message payload. To implement this per spec, one of:
1. Read reviewer's PUBLIC.md after the reviewer responds (need to know the path)
2. Extend `ReInstructOutcome.Responded` to carry response content
3. Accept the deviation and document it

This should be discussed with the spec author. The doer gets a weaker instruction without the counter-reasoning.

### IMPORTANT: Gate 4 self-compaction check

Gate 4 requires "self-compaction check between negotiation steps" but this isn't implemented or tested in the use case. This may be acceptable if self-compaction is fully transparent at the `AgentFacade` level (which it appears to be -- `ReInstructAndAwaitImpl` treats unexpected `SelfCompacted` as crash, and the facade handles it internally). Should be explicitly documented as "handled at facade level" rather than left as an open gap.

## Test Quality Assessment

Tests are well-structured. The `FakeReInstructAndAwait` with dispatch-by-handle is a clean test pattern. The `buildFileReader` helper with call-count-based state is simple and effective for simulating file content changes between reads.

Minor: each `it` block re-executes `sut.execute(...)` independently rather than sharing a result. This is intentional per one-assert-per-test pattern but means each test scenario runs the SUT twice. Acceptable for unit tests.

## Files Reviewed

| File | Lines | Assessment |
|------|-------|------------|
| `RejectionNegotiationUseCase.kt` | 233 | Good. Clean interface + impl. |
| `FeedbackFileReader.kt` | 13 | Good. Minimal interface. |
| `RejectionNegotiationUseCaseImplTest.kt` | 380 | Good. Thorough coverage. |
