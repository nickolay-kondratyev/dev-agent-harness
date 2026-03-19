# Implementation Review: FailedToConvergeUseCase

## Summary

Clean implementation of `FailedToConvergeUseCase` that follows the spec at `doc/use-case/HealthMonitoring.md` (lines 216-234) and aligns well with existing project patterns (`FailedToExecutePlanUseCase`, `ConsoleOutput`). All 7 unit tests pass, `test.sh` and `sanity_check.sh` both exit 0. No pre-existing tests were removed or modified in ways that lose functionality.

**Overall assessment: APPROVE with one important item to address.**

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. Two assertions in one `it` block violates one-assert-per-test standard

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToConvergeUseCaseImplTest.kt` (lines 65-75)

The `"THEN prompt contains correct iteration counts and increment"` test has two `shouldContain` assertions:

```kotlin
it("THEN prompt contains correct iteration counts and increment") {
    val result = buildAndAsk(
        userInput = "N",
        config = defaultConfig,
        currentMax = 10,
        iterationsUsed = 10,
    )
    result.fakeConsole.printedMessages.first() shouldContain "10/10"
    result.fakeConsole.printedMessages.first() shouldContain
        "Grant ${defaultConfig.failedToConvergeIterationIncrement} more iterations?"
}
```

Per CLAUDE.md testing standards: "Each `it` block contains **one logical assertion**." Split into two `it` blocks:
- `"THEN prompt contains iteration counts"` -- checks `"10/10"`
- `"THEN prompt contains increment value"` -- checks `"Grant 2 more iterations?"`

Also consider adding a test for `[y/N]` presence in the prompt, since that is an explicit part of the spec format.

## Suggestions

### 1. Consider adding a whitespace-trimming test

The implementation correctly trims input (`input?.trim()?.uppercase()`), but there is no test that exercises this path. Adding a test for `" y "` (with surrounding whitespace) would document and protect this behavior.

### 2. `UserInputReader.readLine()` is suspend but `DefaultUserInputReader` delegates to a blocking call

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/infra/UserInputReader.kt` (line 16)

```kotlin
override suspend fun readLine(): String? = readlnOrNull()
```

`readlnOrNull()` is a blocking call. In a coroutine context, this will block the coroutine dispatcher thread. When this gets wired into production, consider wrapping with `withContext(Dispatchers.IO)` to avoid blocking the main dispatcher. This is not urgent since the harness is interactive and single-threaded at the operator prompt point, but it is worth a note for when the wiring happens.

### 3. Reuse of `FakeConsoleOutput` across test files

`FakeConsoleOutput` is defined as `internal` in `FailedToExecutePlanUseCaseImplTest.kt` and reused in `FailedToConvergeUseCaseImplTest.kt` via same-package `internal` visibility. This works, but it creates an implicit coupling -- a developer reading `FailedToConvergeUseCaseImplTest.kt` cannot see where `FakeConsoleOutput` comes from without searching. If more test files in this package need it, consider extracting it to a shared `TestFakes.kt` file in the same package. Low priority -- not blocking.

## Documentation Updates Needed

None required. The `HarnessTimeoutConfig` KDoc already references the spec section. The new files have appropriate KDoc.

## Verification

| Check | Result |
|-------|--------|
| `test.sh` | PASSED (exit 0) |
| `sanity_check.sh` | PASSED (exit 0) |
| All 7 new tests | PASSED |
| No pre-existing tests removed | Confirmed |
| Spec compliance (prompt format, binary y/N, fixed increment, injectable I/O) | Confirmed |
| Follows existing patterns (`FailedToExecutePlanUseCase`, `ConsoleOutput`) | Confirmed |
| Constructor injection, no singletons | Confirmed |
| `fun interface` for single-method abstraction | Confirmed |
| BDD style with `AsgardDescribeSpec` | Confirmed |
