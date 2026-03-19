# Implementation Review: RejectionNegotiationUseCase

## Verdict: PASS (with IMPORTANT issues to address)

All tests pass. The implementation is clean, well-structured, and covers the core negotiation flow correctly. Two important issues found related to spec compliance and Gate 4 verification gaps.

---

## IMPORTANT Issues

### 1. Doer compliance message is missing reviewer's counter-reasoning (Spec Deviation)

**Severity:** IMPORTANT

The spec at `doc/plan/granular-feedback-loop.md` line 346-348 explicitly states:

```
message: "Reviewer insists this must be addressed. Their reasoning:
          <reviewer's counter-reasoning from PUBLIC.md>.
          You MUST address this item. Write '## Resolution: ADDRESSED'."
```

The current `buildDoerComplianceMessage` at `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-10/app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt` (line 223-231) only includes a generic "Reviewer insists" message with no actual counter-reasoning from the reviewer:

```kotlin
internal fun buildDoerComplianceMessage(
    feedbackFilePath: Path,
): String = buildString {
    appendLine("Reviewer insists this feedback item must be addressed.")
    appendLine("Feedback file: $feedbackFilePath")
    appendLine()
    appendLine("You MUST address this item. Update the feedback file and write '## Resolution: ADDRESSED'.")
    appendLine("This is non-negotiable — the reviewer is the authority on this item.")
}
```

The reviewer's counter-reasoning is available in the response context but is not captured or forwarded. The `ReInstructOutcome.Responded` only carries `AgentSignal.Done(result)` which does not include message content. This may be a **design limitation of `ReInstructAndAwait`** -- the reviewer's counter-reasoning may need to be read from PUBLIC.md or from the response payload. This warrants alignment with the spec author on how the counter-reasoning should flow.

**Impact:** Without the counter-reasoning, the doer receives a blunt "comply because reviewer says so" instruction rather than the reviewer's actual argument. This reduces the doer's ability to comply intelligently.

### 2. Gate 4 verification gap: self-compaction check between negotiation steps

**Severity:** IMPORTANT

Gate 4 (`doc/plan/granular-feedback-loop.md` line 640) requires:
> "Unit test: self-compaction check between negotiation steps"

This is not tested or implemented. Self-compaction checks at done boundaries are part of the spec's properties (line 358: "Self-compaction friendly: Each negotiation step is a done boundary"). The `RejectionNegotiationUseCase` does not invoke any self-compaction check between the reviewer judgment step and the doer compliance step.

**Note:** This may be intentionally deferred if self-compaction is handled transparently by `AgentFacade` (the facade handles `SelfCompacted` signals internally per `ReInstructAndAwaitImpl`). If so, the Gate 4 item may be satisfied at the facade level rather than the use case level, but this should be explicitly documented/acknowledged.

---

## Suggestions

### 1. `Pair` usage in test file

`FakeReInstructAndAwait.calls` at line 78 of the test file uses `Pair<SpawnedAgentHandle, String>`:

```kotlin
val calls = mutableListOf<Pair<SpawnedAgentHandle, String>>()
```

Per CLAUDE.md, `Pair`/`Triple` should be replaced with descriptive data classes. However, this is a test-internal helper and has minimal impact. Low priority.

### 2. `FailedWorkflow` as a result variant -- explicit spec alignment

The spec R5 (line 525) lists the sealed class as: `Accepted, AddressedAfterInsistence, AgentCrashed`. The implementation adds `FailedWorkflow` as a fourth variant. This is correct behavior (spec lines 337/350 say "On Crashed/FailedWorkflow -> propagate immediately"), and mapping to a distinct result type is cleaner than folding it into `AgentCrashed`. But it's worth noting this is an implementation decision that extends beyond the explicit spec enumeration. This is the right call -- just flagging for awareness.

---

## Positive Observations

- **Clean architecture**: `fun interface` + `Impl` pattern with constructor injection. `FeedbackFileReader` extracted for testability.
- **Exhaustive `when` handling**: All `DoneResult` variants handled without `else`, including the unexpected `COMPLETED` case.
- **Thorough test coverage**: 22 tests across 9 scenario groups covering happy paths, error paths, edge cases (missing marker, invalid marker via SKIPPED), and message template verification.
- **BDD structure**: Tests follow GIVEN/WHEN/THEN with one assert per `it` block.
- **Good use of FakeReInstructAndAwait**: Dispatch-by-handle pattern cleanly simulates two-agent interaction.
- **Proper logging**: Uses `Out`/`OutFactory` with descriptive snake_case messages.
- **No magic strings**: Message templates are in named companion object methods.
