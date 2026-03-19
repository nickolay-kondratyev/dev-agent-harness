# Code Review: TicketShepherd Central Coordinator

## Summary

Implementation of `TicketShepherd` -- the central coordinator driving ticket workflow lifecycle. The change adds the main class, `PartExecutorFactory` and `FinalCommitUseCase` / `TicketStatusUpdater` interfaces, extends `ConsoleOutput` with `printlnGreen`, and provides 19 BDD test cases.

**Overall assessment**: Solid implementation that follows the spec closely. The code is clean, well-structured, and the test coverage is good. A few issues need attention -- one is a genuine bug in the tests, one is a spec deviation, and two required test cases from the ticket are missing.

All tests pass (`./gradlew :app:test` exits 0, `./sanity_check.sh` exits 0). No existing tests were removed or weakened. Modified test files only add the `printlnGreen` override to existing fakes to satisfy the expanded `ConsoleOutput` interface.

---

## CRITICAL Issues

### 1. Self-referencing assertion -- test does not actually verify anything

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt`, line 387

```kotlin
capturedActiveExecutors[0] shouldBe capturedActiveExecutors[0]
```

This compares the value to itself -- it will always pass regardless of what was captured. This is a no-op assertion that gives false confidence. Per CLAUDE.md: "the worst lie is making tests look like they are passing when they are not."

The intent was to verify that `activeExecutor` is non-null (and is the same instance as the executor) during execution. The fix:

```kotlin
capturedActiveExecutors[0] shouldNotBe null
```

Or better yet, since the test already captures `activeExecutor` during `execute()`, assert it is the executor instance itself. Currently `capturedActiveExecutors` captures `shepherd.activeExecutor` -- which should be the executor object, not null. A `shouldNotBe null` assertion would verify the intended behavior.

---

## IMPORTANT Issues

### 2. Missing required test case: "Part with 2 sub-parts creates PartExecutorImpl with reviewerConfig"

The ticket specifies two required test cases:
- "Part with 2 sub-parts creates PartExecutorImpl with reviewerConfig"
- "Part with 1 sub-part creates PartExecutorImpl without reviewerConfig"

Neither test exists in `TicketShepherdTest.kt`. These tests verify the critical routing logic of how `PartExecutorFactory` creates executors differently based on sub-part count. While the current implementation delegates this entirely to the factory (and `TicketShepherd` itself does not contain this logic), these test cases should still exist somewhere -- either in `TicketShepherdTest` (testing end-to-end that the factory receives the right `Part` structure) or in a separate `PartExecutorFactory` implementation test.

If the design intention is that the `PartExecutorFactory` implementation (not yet created) will handle this logic, then the test gap should be explicitly tracked as a follow-up ticket.

### 3. Success message omits ticket ID (spec deviation)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt`, line 113

The spec says (doc/core/TicketShepherd.md, line 47):
> Print success message in green -- e.g., `"Workflow completed successfully for ticket {TICKET_ID}."`

The implementation uses:
```kotlin
internal const val SUCCESS_MESSAGE = "Workflow completed successfully for ticket."
```

The `{TICKET_ID}` placeholder is missing. `TicketShepherd` does not receive a ticket ID in its constructor. This is a spec deviation. Either:
- Add a `ticketId` parameter to `TicketShepherd` (or to `TicketShepherdDeps`) and interpolate it into the message, or
- Align with the spec author that the generic message is acceptable for V1.

### 4. ConsoleOutput: `fun interface` to `interface` is a source-breaking change

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/main/kotlin/com/glassthought/shepherd/core/infra/ConsoleOutput.kt`

Changing from `fun interface` to `interface` and adding a second method is correct -- `fun interface` requires exactly one abstract method. However, this is a breaking change for any callers that used SAM conversion (lambda syntax) to create `ConsoleOutput` instances.

The diff shows three test files were updated, and the `AllSessionsKiller` in `TicketShepherdTest.kt` line 406 still uses SAM conversion since it remains a `fun interface`. This is properly handled. No production callers use SAM conversion for `ConsoleOutput` (verified). This is acceptable.

### 5. `TicketShepherdCreator` not updated to wire `TicketShepherd`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherdCreator.kt`

`TicketShepherdCreatorImpl.create()` still returns `TicketShepherdCreatorResult` which does not include a `TicketShepherd` instance. The class has a comment: `// Future: construct full TicketShepherd`. Since `TicketShepherd` now exists, `TicketShepherdCreator` should be updated to wire and return it. This may be intentionally deferred (the KDoc says "Future tickets will expand this"), but should be tracked as a follow-up.

---

## Suggestions

### 6. Test duplication -- consider extracting common run-and-assert pattern

Every test in the happy path block repeats the same 3 lines:
```kotlin
val fixture = createFixture(parts, results, outFactory)
shouldThrow<TsProcessExitException> { fixture.shepherd.run() }
fixture.fakeXxx.someProperty shouldBe expectedValue
```

The `createFixture` + `shouldThrow` is repeated 9 times for the same input. While one-assert-per-test is the standard (and correctly followed), the GIVEN/WHEN/THEN structure could DRY this up by moving fixture creation and `run()` invocation into a shared `describe("WHEN")` block using `lateinit var` or a `beforeEach`. That said, this is a minor style point -- the current approach is explicit and easy to read.

### 7. `activeExecutor` tracking test has multiple assertions

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt`, lines 347-390

The "THEN activeExecutor is non-null during execution and null after" test checks three things:
1. `capturedActiveExecutors.size shouldBe 1`
2. `capturedActiveExecutors[0] shouldBe capturedActiveExecutors[0]` (the buggy self-reference from CRITICAL #1)
3. `shepherd.activeExecutor shouldBe null`

Per project standards (one assert per test), this should be split into two `it` blocks: one for "non-null during execution" and one for "null after execution."

### 8. `PartExecutorFactory.create` could be `suspend`

The `create` function on `PartExecutorFactory` is not a suspend function, but future implementations may need coroutine context (e.g., for logging via `Out` which is suspend). Consider making it `suspend` now to avoid a breaking interface change later. Low priority since the current usage is straightforward.

### 9. Consider structured logging for the success path

The success message is printed via `consoleOutput.printlnGreen(SUCCESS_MESSAGE)` but is not logged via `Out`. Consider adding an `out.info("workflow_completed_successfully")` alongside the console output for observability/debugging. The failure path (via `FailedToExecutePlanUseCase`) likely logs -- the success path should be symmetric.

---

## Documentation Updates Needed

None required. The implementation PUBLIC.md is accurate. The spec does not need updates (the ticket ID gap is minor and should be resolved as part of the implementation, not by changing the spec).

---

## Checklist Against Required Test Cases

| Required Test Case | Status | Notes |
|---|---|---|
| Happy path: all parts complete -> final commit, status update, cleanup, green success, exit(0) | PASS | Covered by 9 tests in "GIVEN a plan with two parts" |
| FailedWorkflow -> delegates to FailedToExecutePlanUseCase | PASS | Lines 266-295 |
| FailedToConverge -> delegates to FailedToExecutePlanUseCase | PASS | Lines 298-309 |
| AgentCrashed -> delegates to FailedToExecutePlanUseCase | PASS | Lines 312-324 |
| activeExecutor is set during execution and null between parts | PARTIAL | Test exists (line 347) but has self-referencing assertion bug (CRITICAL #1) |
| Final commit is skipped when working tree is clean | MISSING | No test -- `FinalCommitUseCase` is an interface, so "skip if clean" is an implementation detail. Acceptable if tracked. |
| Part with 2 sub-parts creates PartExecutorImpl with reviewerConfig | MISSING | See IMPORTANT #2 |
| Part with 1 sub-part creates PartExecutorImpl without reviewerConfig | MISSING | See IMPORTANT #2 |

---

## Verdict

One CRITICAL bug (self-referencing assertion on line 387) must be fixed before merge. The missing test cases (IMPORTANT #2) and spec deviation on ticket ID (IMPORTANT #3) should be addressed or explicitly tracked as follow-ups.
