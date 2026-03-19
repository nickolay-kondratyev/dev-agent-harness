# Implementation Review -- TicketShepherdCreator

## Summary

The implementation introduces `TicketShepherdCreator` interface and `TicketShepherdCreatorImpl` in the
`com.glassthought.shepherd.core.creator` package. It correctly implements all 11 steps from the spec:
workflow parsing, ticket parsing/validation, working tree validation, git setup (originating branch,
try-N, feature branch), .ai_out/ structure, CurrentState initialization/flush, and TicketShepherd
wiring. The old partial implementation in `core.TicketShepherdCreator` is marked as superseded and
retained for backward compatibility. Tests pass (13 test cases). Sanity check passes.

**Overall assessment**: Solid implementation. A few important issues around test coverage gaps and
DRY violations, plus one correctness concern with suspend calls in describe blocks.

---

## CRITICAL Issues

None found.

---

## IMPORTANT Issues

### 1. Missing test for blank `title` -- test coverage gap

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`

The spec requires: "Ticket with missing `title` -> clear error". The implementation validates it
(line 261: `if (ticketData.title.isBlank()) add("title")`), but there is NO test for a ticket with
a blank title. The tests cover blank `id`, null `status`, and wrong `status`, but `title` is missing.

**Fix**: Add a test case for `VALID_TICKET_DATA.copy(title = "")` that expects an
`IllegalStateException` mentioning "title".

### 2. Suspend calls in `describe` block bodies -- fragile test execution

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`

Multiple describe blocks call `creator.create(...)` directly in the describe body (lines 295, 310,
325, 347, 364, 380, 395, 424, 444). The CLAUDE.md states: "describe block bodies are NOT suspend
contexts. Suspend calls must go inside it or afterEach blocks."

While Kotest DescribeSpec `describe` blocks do accept suspend lambdas (so this works), the CLAUDE.md
convention explicitly discourages this pattern. The concern is that the `create()` call is shared
setup that runs once at describe-block registration time, not per-test. If one of the assertions
within the block mutates state or if test isolation is needed, this pattern can lead to flaky tests.

**Fix**: Move `creator.create(...)` calls into a `lateinit var` or use a `beforeEach`/`beforeTest`
pattern, or restructure so the create call is inside each `it` block. Alternatively, if this is an
accepted project pattern, update CLAUDE.md to reflect the actual convention.

### 3. `createTestShepherdContext()` is duplicated across two test files

**Files**:
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt` (lines 225-257)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt` (lines 148-175)

Both files contain nearly identical `createTestShepherdContext()` functions (~30 lines each). This is
a DRY violation for non-trivial infrastructure setup. If `ShepherdContext` constructor changes, both
must be updated.

**Fix**: Extract to a shared test utility (e.g., `TestShepherdContextFactory` in a test support
package).

### 4. Same class name `TicketShepherdCreatorTest` in two packages

**Files**:
- `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`

Both test classes are named `TicketShepherdCreatorTest`. While they compile (different packages), this
creates confusion in test reports and IDE navigation. When running tests by name, it is unclear which
is which.

**Fix**: Since the old one is for the superseded implementation, consider renaming it to
`LegacyTicketShepherdCreatorTest` or creating a follow-up ticket to remove it once the old code is
fully deprecated.

### 5. `FakeConsoleOutput` and `FakeProcessExiter` duplicated across test files

Both the old and new test files define identical `FakeConsoleOutput`, `FakeProcessExiter`, and
`FakeAllSessionsKiller` classes. This is another DRY violation.

**Fix**: Extract shared test fakes to a common test-support location.

---

## Suggestions

### 1. `StateSetupResult.currentStatePersistence` uses concrete type

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` (line 300)

```kotlin
private data class StateSetupResult(
    val aiOutputStructure: AiOutputStructure,
    val currentState: CurrentState,
    val currentStatePersistence: CurrentStatePersistenceImpl,  // <-- concrete type
)
```

Per DIP (Dependency Inversion Principle), this should use the `CurrentStatePersistence` interface
instead of `CurrentStatePersistenceImpl`. The `InterruptHandlerImpl` constructor accepts the
interface, so there is no reason to carry the concrete type.

### 2. Consider verifying CurrentState contents in with-planning test

The "with-planning workflow" test only checks `result.tryNumber shouldBe 1`. It does not verify that
the CurrentState was initialized with planning parts (not execution parts). This would be a valuable
behavioral assertion that validates the `CurrentStateInitializer` integration.

### 3. Temp directory cleanup

Tests create temp directories via `Files.createTempDirectory(...)` but do not clean them up. While
not critical for CI (JVM temp dirs are eventually cleaned), adding `afterSpec { tempDir.toFile().deleteRecursively() }` would be cleaner.

### 4. `repoRoot` default uses `System.getProperty("user.dir")`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` (line 136)

Using `System.getProperty("user.dir")` as a default parameter in the constructor is not ideal
because it captures the value at construction time and is hard to test. However, since tests
explicitly override this via `repoRoot = tempDir`, this is acceptable for now. Just note that the
production wiring site must pass the correct repo root explicitly.

---

## Documentation Updates Needed

None required. The spec at `doc/core/TicketShepherdCreator.md` aligns with the implementation.
The anchor point `ap.cJbeC4udcM3J8UFoWXfGh.E` was correctly moved to the new implementation.
