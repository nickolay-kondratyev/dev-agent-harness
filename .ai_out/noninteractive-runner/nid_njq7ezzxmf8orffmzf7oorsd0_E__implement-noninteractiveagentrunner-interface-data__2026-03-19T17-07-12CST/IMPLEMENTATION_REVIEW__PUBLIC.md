# Implementation Review: NonInteractiveAgentRunner

## VERDICT: PASS_WITH_SUGGESTIONS

## Summary

The implementation introduces `NonInteractiveAgentRunner` -- a lightweight subprocess-based agent invocation mechanism for utility tasks that don't need interactive TMUX sessions. The interface, data classes, sealed result type, command construction, and result mapping all match the spec (`doc/core/NonInteractiveAgentRunner.md`). Shell escaping uses the safe single-quote `'\''` idiom. Tests are BDD-style with one assert per `it` block and cover command construction for both agent types, all three result types (Success/Failed/TimedOut), combined stdout+stderr output, and shell escaping of single quotes.

All 19 tests pass. `sanity_check.sh` and `test.sh` both pass clean.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `Pair` usage in test helper violates CLAUDE.md standards

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-7/app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImplTest.kt` (line 35)

**What**: The `buildRunner` helper returns `Pair<NonInteractiveAgentRunnerImpl, FakeProcessRunner>` and uses destructuring. CLAUDE.md explicitly states: "No `Pair`/`Triple` -- create descriptive `data class`."

**Why it matters**: `Pair` obscures what `first` and `second` mean. A named data class is self-documenting.

**Suggested fix**: Create a simple data class in the test file:
```kotlin
data class RunnerWithFake(
    val runner: NonInteractiveAgentRunnerImpl,
    val fakeProcessRunner: FakeProcessRunner,
)
```

### 2. Missing test: timeout value is forwarded to ProcessRunner

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-7/app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImplTest.kt`

**What**: The `FakeProcessRunner` captures `lastTimeout` but no test asserts that the request's `timeout` is correctly passed through to `processRunner.runProcessV2`. This is a core contract of the spec (Section "Execution", step 3: "Await process completion with `timeout`").

**Why it matters**: If someone accidentally hardcodes or drops the timeout parameter, no test would catch it. The timeout is the **only safeguard** against hanging processes (as the spec itself emphasizes).

**Suggested fix**: Add a test in the "GIVEN a CLAUDE_CODE agent request / WHEN run is called" section:
```kotlin
it("THEN the request timeout is forwarded to ProcessRunner") {
    runner.run(buildRequest(timeout = 20.minutes))
    fake.lastTimeout shouldBe 20.minutes
}
```

---

## Suggestions

### 1. Consider testing with empty instructions

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-7/app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImplTest.kt`

**What**: There's no test for edge cases like empty string instructions or instructions with special shell characters beyond single quotes (e.g., `$`, backticks, newlines). The shell escaping is correct (single-quote wrapping handles all of these), but a test with `$HOME` or backtick content would serve as a regression guard documenting this safety property.

### 2. `combineOutput` could be a top-level `private` function or extension

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-7/app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImpl.kt` (line 81)

**What**: `combineOutput` and `shellEscape` are in the `companion object` but `combineOutput` is `private` while `shellEscape` is `internal`. Both are stateless utility functions with no dependency on instance state. Placing them in a companion object is acceptable per CLAUDE.md ("for stateless utilities, use a static class"), so this is fine as-is. Just noting for awareness.

### 3. Minor: `shouldBeInstanceOf` is more idiomatic than `is` check

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-7/app/src/test/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImplTest.kt` (lines 122, 151, 176)

**What**: The tests use `(result is NonInteractiveAgentResult.Success) shouldBe true` instead of `result.shouldBeInstanceOf<NonInteractiveAgentResult.Success>()`. The latter gives a better failure message.

---

## Documentation Updates Needed

None -- the spec (`doc/core/NonInteractiveAgentRunner.md`) is already accurate and matches the implementation.
