# Implementation Review: Wire InterruptHandler into TicketShepherdCreator

## Summary

This change introduces `TicketShepherdCreator` (interface + `TicketShepherdCreatorImpl`) that wires
`InterruptHandlerImpl` with all 6 required production dependencies and calls `install()` during
`create()`. A `TicketShepherdCreatorResult` data class is returned holding the wired components.
Five unit tests cover the wiring.

**Overall assessment**: Clean, well-structured implementation that follows project standards. The
wiring is correct, dependencies are properly injected via constructor, and the `install()` call
is placed at the right point. Two issues worth addressing below.

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. Every test calls `install()`, which registers a real JVM SIGINT handler as a side effect

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-8/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`

Every `it` block calls `creator.create()`, which calls `interruptHandler.install()`, which calls
`Signal.handle(Signal("INT"))` -- registering a real JVM signal handler. This is a global JVM
side effect that persists across the test process. While it works today because the test fakes
are harmless, this is fragile:

- If tests run in parallel (Kotest supports this), the last `install()` wins, potentially
  swallowing a handler set by another test.
- It couples TicketShepherdCreator unit tests to InterruptHandler's internal implementation
  (JVM signal registration).

**Suggestion**: Extract the `install()` call out of `create()` and make it an explicit step the
caller performs after `create()` returns. Alternatively, accept an `InterruptHandler` factory or
make `install` mockable. The simplest fix consistent with current design: the creator could accept
an optional `installHandler: Boolean = true` parameter, defaulting to true in production, and tests
pass `false`. But the cleanest approach architecturally is to **not** call `install()` inside
`create()` at all -- let the caller (CLI entry point) do `result.interruptHandler.install()`. This
keeps `create()` as pure wiring with no side effects, and the caller controls when the signal
handler activates.

### 2. Significant test code duplication (DRY violation)

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-8/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt`

The creator construction block is repeated nearly identically in all 5 `it` blocks (lines 77-85,
93-101, 109-117, 125-133, 146-154). Per CLAUDE.md, DRY is "much less important in tests" but this
is excessive -- the same 8 lines repeated 5 times with only minor variations (which fakes are
captured).

**Suggestion**: Use a GIVEN-level helper function or a `lateinit var` + `beforeEach` to construct
the creator once per describe block. BDD structure stays clean:

```kotlin
describe("WHEN create() is called") {
    val killerFactory = TrackingKillerFactory()
    val fakeConsole = FakeConsoleOutput()
    // ... construct creator once ...

    lateinit var result: TicketShepherdCreatorResult

    beforeEach {
        result = creator.create()
    }

    it("THEN returns a result with an InterruptHandlerImpl") {
        result.interruptHandler.shouldBeInstanceOf<InterruptHandlerImpl>()
    }
    // ...
}
```

## Suggestions

### 3. `createTestShepherdContext()` constructs real `TmuxCommandRunner` and `TmuxSessionManager`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-8/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt` (lines 178-204)

The test helper creates real Tmux infrastructure classes. This works because the factory lambda
overrides the `AllSessionsKiller` construction so tmux is never actually invoked. However, this is
fragile -- if `TicketShepherdCreatorImpl` later adds logic that touches `shepherdContext.infra.tmux`
directly, tests will break or require tmux to be installed.

Consider creating a `FakeShepherdContext` or a builder that uses no-op/fake implementations for
all infra components. This would also DRY up if other test files need a `ShepherdContext`.

### 4. The `shouldNotBe null` test (line 122) is low-value

The test "THEN returns a non-null CurrentStatePersistence" asserts `result.currentStatePersistence shouldNotBe null`. Since `CurrentStatePersistenceImpl` is constructed directly and `TicketShepherdCreatorResult` has a non-nullable `currentStatePersistence` field, this can never be null at compile time. The Kotlin type system already guarantees this. Consider replacing it with a more meaningful assertion, such as verifying the persistence is wired with the correct `AiOutputStructure`.

### 5. Minor: `TicketShepherdCreatorResult` exposes `InterruptHandler` interface but test casts to `InterruptHandlerImpl`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-8/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt` (line 159)

The test `(result.interruptHandler as InterruptHandlerImpl).handleSignal()` casts through the
interface to call `internal` method `handleSignal()`. This is acceptable for testing but couples the
test to the concrete type. This is a minor observation, not a blocker -- the test is proving correct
wiring which justifies peeking behind the interface.

## Documentation Updates Needed

None required. The KDoc on `TicketShepherdCreator` and `TicketShepherdCreatorImpl` is thorough,
anchor points are properly maintained, and future TODOs are documented in comments and KDoc rather
than `TODO()` markers (avoiding detekt's ForbiddenComment rule).
