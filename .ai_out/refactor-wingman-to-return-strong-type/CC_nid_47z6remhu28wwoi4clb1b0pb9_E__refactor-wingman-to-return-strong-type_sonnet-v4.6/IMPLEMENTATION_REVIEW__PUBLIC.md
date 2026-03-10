# Implementation Review: Refactor Wingman to Return Strong Type

**Verdict: PASS with one IMPORTANT issue**

---

## Summary

The implementation refactors `Wingman.resolveSessionId` from returning `String` to returning
`ResumableAgentSessionId(agentType: AgentType, sessionId: String)`. Two new types were
created (`AgentType` enum, `ResumableAgentSessionId` data class), the interface and its
sole implementation were updated, and all 12 tests pass with the correct assertions.

The refactoring achieves its goal cleanly. There is one pre-existing structural issue in
`ClaudeCodeWingman` that was not introduced by this change but is worth calling out.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `var guidScanner` with post-construction reassignment in secondary constructor

**File:** `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`

The secondary constructor delegates to the primary constructor with `PLACEHOLDER_PATH`,
which initialises `guidScanner = FilesystemGuidScanner(PLACEHOLDER_PATH)` in the property
initialiser. Immediately after, the secondary constructor body reassigns
`this.guidScanner = guidScanner`.

```kotlin
// Primary constructor path sets this first...
private var guidScanner: GuidScanner = FilesystemGuidScanner(claudeProjectsDir)

// ...then the secondary constructor overwrites it.
internal constructor(guidScanner: GuidScanner, ...) : this(PLACEHOLDER_PATH, ...) {
    this.guidScanner = guidScanner  // mutable post-init reassignment
}
```

This approach has two problems:

1. **Mutability where none is needed.** The field is `val`-able from every construction
   path's perspective. Using `var` to work around the two-constructor problem hides that
   the field never changes after construction.

2. **Wasted allocation.** The `FilesystemGuidScanner(PLACEHOLDER_PATH)` instance is
   always constructed in the primary path and then discarded when the secondary constructor
   overrides it.

The idiomatic Kotlin fix is to make `GuidScanner` a constructor parameter and derive it
internally for the production path — eliminating `PLACEHOLDER_PATH` and the mutation:

```kotlin
class ClaudeCodeWingman private constructor(
    private val guidScanner: GuidScanner,
    outFactory: OutFactory,
    private val resolveTimeoutMs: Long = 45_000L,
    private val pollIntervalMs: Long = 500L,
) : Wingman {

    // Production constructor — callers supply a filesystem path.
    constructor(
        claudeProjectsDir: Path,
        outFactory: OutFactory,
        resolveTimeoutMs: Long = 45_000L,
        pollIntervalMs: Long = 500L,
    ) : this(
        guidScanner = FilesystemGuidScanner(claudeProjectsDir),
        outFactory = outFactory,
        resolveTimeoutMs = resolveTimeoutMs,
        pollIntervalMs = pollIntervalMs,
    )

    // Test constructor — callers inject a fake scanner.
    internal constructor(
        guidScanner: GuidScanner,
        outFactory: OutFactory,
        resolveTimeoutMs: Long = 45_000L,
        pollIntervalMs: Long = 500L,
    ) : this(
        guidScanner = guidScanner,
        outFactory = outFactory,
        resolveTimeoutMs = resolveTimeoutMs,
        pollIntervalMs = pollIntervalMs,
    )
    ...
}
```

Note: this issue pre-exists this PR. This PR neither introduced it nor worsened it. It
is called out here because the PR touched this class and the pattern is at odds with the
CLAUDE.md principle "favor immutability — immutable data structures by default". Flag as
a follow-up ticket.

---

## Suggestions

### a. `agentType` is not tested in isolation

All three `resolveSessionId` success-path assertions compare the full
`ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)` object via `shouldBe`. Because
`data class` equality checks all fields, the `agentType = CLAUDE_CODE` invariant is
implicitly covered. No separate `it("THEN agentType is CLAUDE_CODE")` block is strictly
needed; the current approach is acceptable.

However, a reader scanning test names alone cannot tell that `agentType` is verified. An
additional split-assertion test per CLAUDE.md one-assert-per-it would make this explicit
and self-documenting:

```kotlin
it("THEN agentType is CLAUDE_CODE") {
    result.agentType shouldBe AgentType.CLAUDE_CODE
}
it("THEN sessionId is extracted from filename") {
    result.sessionId shouldBe sessionId
}
```

This is a suggestion, not a blocker.

### b. `matches.size` in debug log uses `String` `ValType`

In `pollUntilFound`, `matches.size.toString()` is logged with `ValType.STRING_USER_AGNOSTIC`.
A more semantically specific `ValType` (e.g., `COUNT` or `INTEGER`) would better match
CLAUDE.md's requirement that `ValType` is "semantically specific to the value being logged."
Minor concern; pre-existing pattern in this file.

---

## Checklist

| Criterion | Result |
|---|---|
| `AgentType` enum correct (`CLAUDE_CODE`, `PI`) | PASS |
| `ResumableAgentSessionId` is a `data class` with `agentType` and `sessionId` | PASS |
| `Wingman` interface updated to return `ResumableAgentSessionId` | PASS |
| `ClaudeCodeWingman` returns `ResumableAgentSessionId(AgentType.CLAUDE_CODE, sessionId)` | PASS |
| All 12 tests pass | PASS |
| Tests follow BDD GIVEN/WHEN/THEN structure | PASS |
| Tests inherit `outFactory` from `AsgardDescribeSpec` (no `NoOpOutFactory` constructed manually) | PASS |
| No silent fallbacks or skipped individual tests | PASS |
| `agentType` field covered by assertions (via full-object `shouldBe`) | PASS (implicit) |
| No production callers broken | PASS (no production callers existed) |
| No anchor points removed | PASS |
| `var guidScanner` mutable post-init reassignment | IMPORTANT — pre-existing, needs follow-up ticket |
