# Implementation Review: SessionEntry + PendingQuestion Data Classes

**Branch:** `nid_5o5wyxuzoz7qrkuq4wuo2gnjr_E__implement-sessionentry-pendingquestion-data-classe__2026-03-19T17-45-02CST`
**Verdict:** APPROVE with one IMPORTANT issue to address.

---

## Summary

This change introduces three new data classes (`SessionEntry`, `PendingQuestion`, `UserQuestionContext`) and a test class (`SessionEntryTest`) for the live session registry. The implementation faithfully follows the spec at `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) and `doc/core/UserQuestionHandler.md` (ref.ap.NE4puAzULta4xlOLh5kfD.E). All 6 specified test cases are present and passing. No existing code was removed or modified. PayloadId was correctly reused (not recreated).

Overall assessment: Clean, spec-compliant implementation. One design concern worth addressing now before consumers depend on it.

---

## No CRITICAL Issues

No security, correctness, or data loss issues found.

---

## IMPORTANT Issues

### 1. `question` field duplicated between `PendingQuestion` and `UserQuestionContext`

**Files:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-9/app/src/main/kotlin/com/glassthought/shepherd/core/session/PendingQuestion.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-9/app/src/main/kotlin/com/glassthought/shepherd/core/session/UserQuestionContext.kt`

`PendingQuestion` has a `question: String` field, and `UserQuestionContext` (which is nested inside `PendingQuestion.context`) also has a `question: String` field. The test helper confirms they carry the same value:

```kotlin
private fun createTestPendingQuestion(): PendingQuestion = PendingQuestion(
    question = "test question?",
    context = UserQuestionContext(
        question = "test question?",   // <-- same value as PendingQuestion.question
        ...
    ),
)
```

This is a DRY violation that creates a synchronization hazard -- nothing enforces that `pendingQuestion.question == pendingQuestion.context.question`. A consumer could construct a `PendingQuestion` where the two values diverge, leading to subtle bugs depending on which field is read.

**The spec itself has this duplication** (the `PendingQuestion` shape in SessionsState.md line 93-96 and the `UserQuestionContext` shape in UserQuestionHandler.md line 24-30 both include `question`). This suggests the spec should be updated too.

**Recommendation:** Remove `question` from `PendingQuestion` since it is already carried by `context.question`. `PendingQuestion` then becomes:

```kotlin
data class PendingQuestion(
    val context: UserQuestionContext,
)
```

Or if `PendingQuestion` becomes a single-field wrapper, consider whether the class is even needed -- the queue could be `ConcurrentLinkedQueue<UserQuestionContext>` directly. However, keeping `PendingQuestion` as a named wrapper is reasonable if the class is expected to grow (e.g., with an `answeredAt` timestamp in V2).

If spec alignment is required before making this change, create a follow-up ticket to address the duplication.

### 2. `SessionEntry` as `data class` with mutable, identity-sensitive fields

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-9/app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt`

`SessionEntry` is declared as a `data class`, but it contains:
- `ConcurrentLinkedQueue<PendingQuestion>` -- mutable, and `equals`/`hashCode` on queues compares contents, not identity
- `CompletableDeferred<AgentSignal>` -- mutable, identity-based semantics

This makes the auto-generated `equals()`, `hashCode()`, `copy()`, and `toString()` methods from `data class` semantically misleading:
- Two `SessionEntry` instances with the same queue contents but different queue instances would be considered "equal" by `equals()`, even though they are logically distinct sessions.
- `copy()` would share the same `ConcurrentLinkedQueue` and `CompletableDeferred` references, creating hidden aliasing bugs if a consumer naively copies and mutates.

**Mitigating factor:** The ticket spec explicitly uses `data class`. This is likely intentional for destructuring and `.toString()` convenience. The class is also internal to `AgentFacadeImpl` and `ShepherdServer`, limiting the blast radius.

**Recommendation:** This is acceptable for V1 given the spec explicitly calls for `data class` and the limited consumer surface. However, add a KDoc warning about `copy()` aliasing, or consider switching to a plain `class` with a manual `toString()` if `data class` features are not actually needed. If this is deferred, create a follow-up ticket.

---

## Suggestions

### 1. Test helper duplication: `noOpCommunicator` and `noOpExistsChecker`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-9/app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionEntryTest.kt`

These no-op test doubles (lines 82-86) are likely duplicated across test files that deal with `TmuxSession` and `TmuxAgentSession`. Consider extracting them into a shared test fixture class (e.g., `TestTmuxDoubles` or a `TmuxAgentSessionTestFactory`) to DRY up test infrastructure. This is low priority for this PR but worth a follow-up ticket if the pattern repeats.

### 2. KDoc on `SessionEntry` could reference `PendingQuestion` for discoverability

The `SessionEntry` KDoc references `[SessionsState]` and `[ConcurrentLinkedQueue]` but does not link to `[PendingQuestion]`. Adding a `[PendingQuestion]` link would improve IDE navigation.

---

## Documentation Updates Needed

None required for CLAUDE.md. The spec docs (`SessionsState.md`, `UserQuestionHandler.md`) should have the `question` field duplication between `PendingQuestion` and `UserQuestionContext` addressed (see IMPORTANT issue #1 above), but that is a spec-level change outside the scope of this implementation ticket.

---

## Tests

All 6 specified test cases are present and passing:
1. Empty queue -> `isQAPending` false
2. Non-empty queue -> `isQAPending` true
3. `subPartIndex` 0 -> `DOER`
4. `subPartIndex` 1 -> `REVIEWER`
5. Adding to queue -> `isQAPending` becomes true
6. Draining queue -> `isQAPending` becomes false

Test structure follows BDD/GIVEN-WHEN-THEN, extends `AsgardDescribeSpec`, one assert per `it` block. Compliant with project testing standards.
