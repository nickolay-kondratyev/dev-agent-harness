# Implementation Review: Refactor to be more OOP

**VERDICT: APPROVED_WITH_MINOR_ISSUES**

The implementation correctly delivers all four requirements and the build passes. The OOP structure is clean and the design decisions (lambda for existsChecker, interface/impl split, private sessionExists) are sound.

---

## Summary

The refactoring extracted a `TmuxCommunicator` interface from what was previously a concrete class, added a `TmuxSession` OOP handle that bundles session name + communicator + existence check, updated `TmuxSessionManager` to return and accept `TmuxSession` objects, and wired everything correctly in `App.kt`. Tests were updated to use the new API.

---

## Build / Test Status

- `./gradlew :app:build` — PASSES
- `./gradlew :app:test` (unit tests, no integ) — PASSES
- `./gradlew :app:test -PrunIntegTests=true` — FAILS with `java.io.IOException` on all tmux tests. This is an environment issue: `tmux` is not installed in the review environment. The failures are not caused by the refactoring — the exceptions occur at `ProcessBuilder` invocation, which is the same low-level call that existed before this change. Not a blocking concern.

---

## Requirements Checklist

| # | Requirement | Status |
|---|-------------|--------|
| 1 | Add interface to `TmuxCommunicator` | Done — interface + `TmuxCommunicatorImpl` in same file |
| 2 | Add `TmuxSession` OOP class with communicator + sessionless `sendKeys`/`sendRawKeys` | Done |
| 3 | `TmuxSessionManager.createSession()` returns `TmuxSession` | Done |
| 4 | `TmuxSession.exists()` instead of `TmuxSessionManager.sessionExists()` | Done — `sessionExists` is now `private` |
| 5 | Anchor point `ap.3BCYPiR792a2B8I9ZONDwmvN.E` on impl class | Done — on `TmuxCommunicatorImpl` |
| 6 | `sessionExists` made private | Done |
| 7 | `TmuxCommandRunner` doc reference updated to `TmuxCommunicatorImpl` | **MISSING — see below** |

---

## IMPORTANT Issues

### 1. `TmuxCommandRunner` KDoc still references the old class name

File: `app/src/main/kotlin/com/glassthought/tmux/util/TmuxCommandRunner.kt`, line 9

```kotlin
* Shared infrastructure for [com.glassthought.tmux.TmuxSessionManager] and [com.glassthought.tmux.TmuxCommunicator].
```

The reference `[com.glassthought.tmux.TmuxCommunicator]` now points to the *interface*, not the implementation. The comment's intent was to name the concrete consumers that use this runner. The correct reference should be `[com.glassthought.tmux.TmuxCommunicatorImpl]`.

This was listed as an explicit review criterion and was not addressed.

---

### 2. Duplicate test case in `TmuxSessionManagerTest`

File: `app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt`

Two `describe` blocks both assert `session.exists() shouldBe true` on a freshly created session:

```kotlin
describe("WHEN createSession with bash") {
    it("THEN session exists") { ... session.exists() shouldBe true }
}

describe("WHEN session is created") {
    it("THEN exists() returns true") { ... session.exists() shouldBe true }
}
```

These are identical in behavior (different phrasing, same assertion). One should be removed. The second block (`WHEN session is created / THEN exists() returns true`) is the more descriptive one and should be kept. The first one (`WHEN createSession with bash / THEN session exists`) is the redundant one.

---

## Suggestions

### 3. Fully-qualified `TmuxSessionName` constructor call in `TmuxSessionManager`

File: `app/src/main/kotlin/com/glassthought/tmux/TmuxSessionManager.kt`, line 49

```kotlin
name = com.glassthought.tmux.data.TmuxSessionName(sessionName),
```

`TmuxSessionName` is used fully-qualified while the rest of the file does not import it. It should be added as a proper import at the top and referenced by simple name. This is inconsistent with the rest of the codebase and reduces readability.

---

## Overall Assessment

The core OOP design is correct and well-structured. The lambda-based `existsChecker` is the right call to avoid circular dependency. DIP is applied correctly (interface declared in same file as impl, consumers depend on interface). SRP is respected. Logging follows the project conventions. The `@AnchorPoint` annotation is correctly preserved on `TmuxCommunicatorImpl`.

Two issues require a fix before merge:
- The stale `TmuxCommandRunner` KDoc reference (explicit review requirement that was missed)
- The duplicate test case (test quality issue — reduces signal-to-noise)

The missing import (issue 3) is a minor style inconsistency but worth cleaning up in the same pass.
