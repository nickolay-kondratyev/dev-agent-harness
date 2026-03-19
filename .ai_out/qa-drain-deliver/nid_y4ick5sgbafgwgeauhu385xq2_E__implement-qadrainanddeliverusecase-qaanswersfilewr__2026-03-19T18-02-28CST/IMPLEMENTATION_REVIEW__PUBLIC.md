# Implementation Review: QaDrainAndDeliverUseCase + QaAnswersFileWriter

## Summary

Implementation adds a clean QA drain-and-deliver pipeline: `QaDrainAndDeliverUseCase` drains a
`SessionEntry.questionQueue`, collects answers via `UserQuestionHandler`, writes them via
`QaAnswersFileWriter`, and delivers via `AckedPayloadSender`. Tests are solid and cover the
key scenarios including late arrivals during processing.

Overall quality is good. Two issues need attention before merge -- one spec compliance bug
and one correctness concern with multiline questions.

**Tests pass**: `./test.sh` and `./sanity_check.sh` both exit 0.

---

## IMPORTANT Issues

### 1. [IMPORTANT] Spec Compliance: Header mismatch -- `QA Answers` vs `Q&A Answers`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/question/QaAnswersFileWriter.kt`, line 45

The spec at `doc/core/UserQuestionHandler.md` line 114 specifies:

```markdown
## Q&A Answers
```

The implementation writes:

```kotlin
sb.appendLine("## QA Answers")
```

This is `QA Answers` (no ampersand) vs the spec's `Q&A Answers`. The agent reading this file
may pattern-match on the exact header text. This should match the spec exactly.

**Fix**: Change line 45 to `sb.appendLine("## Q&A Answers")` and update the corresponding
test assertions in `QaAnswersFileWriterTest.kt` (line 31) and `QaDrainAndDeliverUseCaseTest.kt`
(line 215).

---

### 2. [IMPORTANT] Multiline questions break blockquote formatting

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/question/QaAnswersFileWriter.kt`, line 50

```kotlin
sb.appendLine("> ${qa.question}")
```

If `qa.question` contains newlines, only the first line will be blockquoted. For example,
a question like `"Line 1\nLine 2"` would render as:

```markdown
> Line 1
Line 2
```

instead of:

```markdown
> Line 1
> Line 2
```

**Fix**: Prefix each line of the question with `> `:

```kotlin
qa.question.lines().forEach { line ->
    sb.appendLine("> $line")
}
```

Add a test case for multiline questions to `QaAnswersFileWriterTest.kt`.

---

### 3. [IMPORTANT] Duplicate `UserQuestionContext` data classes across packages

**Files**:
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/session/UserQuestionContext.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/question/UserQuestionContext.kt`

These are identical data classes in two different packages. The `QaDrainAndDeliverUseCase`
has a `toQuestionContext()` companion function (line 87-94) that manually maps field-by-field.
This is a DRY violation and a maintenance trap -- if a field is added to one but not the other,
the mapping silently becomes stale.

The exploration doc (line 21) already identified this: *"These are identical but in different
packages."*

**Recommendation**: This is pre-existing tech debt, not introduced by this PR. However, since
this PR introduces a manual mapping function that bridges the two, this is the right time to
consolidate. Either:

1. **Delete** `com.glassthought.shepherd.core.question.UserQuestionContext` and have
   `UserQuestionHandler` take the session-package version directly, OR
2. **Delete** `com.glassthought.shepherd.core.session.UserQuestionContext` and have
   `PendingQuestion` use the question-package version.

If consolidation is deferred, create a follow-up ticket so it does not get lost.

---

## Suggestions

### 4. [SUGGESTION] Logging: use `ValType.COUNT` for question counts and `ValType.FILE_PATH_STRING` for file path

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCase.kt`

Lines 45, 60 use `ValType.STRING_USER_AGNOSTIC` for numeric counts, and line 69 uses it for
a file path. The codebase already uses `ValType.COUNT` (e.g., `PlanFlowConverter.kt:101`) and
`ValType.FILE_PATH_STRING` (e.g., `TicketParser.kt:52`). Using semantically specific ValTypes
improves structured log querying.

```kotlin
// Line 45: question ordinal
Val((collectedQAs.size + 1).toString(), ValType.COUNT),

// Line 60: total count
Val(collectedQAs.size.toString(), ValType.COUNT),

// Line 69: file path
Val(filePath.toAbsolutePath().toString(), ValType.FILE_PATH_STRING),
```

---

### 5. [SUGGESTION] `QaAnswersFileWriterImpl` could be a `companion object` factory or `object`

Since `QaAnswersFileWriterImpl` is stateless (no constructor parameters, no mutable state), it
could be an `object` instead of a `class`. However, given the interface+impl pattern used throughout
the codebase, the current approach is consistent. No action needed -- just noting.

---

### 6. [SUGGESTION] Test helper cleanup: `QaDrainAndDeliverUseCaseTest` test helpers could be extracted

The test file at `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/test/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCaseTest.kt`
has substantial test infrastructure (lines 268-359) including `FakeUserQuestionHandler`,
`RecordingAckedPayloadSender`, `createTestSessionEntry()`, and `createTestTmuxAgentSession()`.

The `RecordingAckedPayloadSender` and `createTestSessionEntry`/`createTestTmuxAgentSession`
helpers will likely be needed by other test classes. Consider extracting to a shared test-support
package when additional consumers emerge.

---

### 7. [SUGGESTION] Test: "THEN questions were received in order" has 3 assertions

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/test/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCaseTest.kt`, lines 102-106

```kotlin
it("THEN questions were received in order") {
    fakeHandler.receivedQuestions[0] shouldBe "Q1?"
    fakeHandler.receivedQuestions[1] shouldBe "Q2?"
    fakeHandler.receivedQuestions[2] shouldBe "Q3?"
}
```

Per CLAUDE.md testing standards: "Each `it` block contains one logical assertion." This is
borderline -- asserting ordered collection content could be considered one logical assertion.
A cleaner alternative:

```kotlin
it("THEN questions were received in order") {
    fakeHandler.receivedQuestions shouldBe listOf("Q1?", "Q2?", "Q3?")
}
```

This is a single assertion that verifies both ordering and content.

Same applies to lines 72-74 and 77-80 in `QaAnswersFileWriterTest.kt`.

---

## Items Verified (No Issues Found)

- **Drain loop correctness**: The `while(true) { poll() ?: break }` pattern is correct.
  `ConcurrentLinkedQueue.poll()` returns null when empty, which breaks the loop. There is no
  infinite loop risk because the handler is called synchronously -- the loop can only iterate
  once per queued item plus any items added during handler execution.
- **No resource leaks**: File I/O uses `writeText` (auto-closes), no streams left open.
- **Thread safety**: `ConcurrentLinkedQueue` is the correct choice for the multi-actor scenario
  described in the spec.
- **Empty queue handling**: Properly short-circuits with early return and no file write.
- **Interface design**: `AckedPayloadSender` in `core.server` package is appropriate -- it
  deals with the agent-to-server communication layer.
- **Constructor injection**: All dependencies injected via constructor. No singletons.
- **No swallowed exceptions**: No try-catch blocks that hide failures.
- **No removed tests or anchor points**: No pre-existing functionality removed.
