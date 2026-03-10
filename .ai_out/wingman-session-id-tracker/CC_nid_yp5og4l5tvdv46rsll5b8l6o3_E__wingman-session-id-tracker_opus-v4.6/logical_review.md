# Logical Review: Wingman Session ID Tracker

**Verdict**: READY

## Summary

Introduces the `Wingman` interface and `ClaudeCodeWingman` implementation for discovering Claude Code session IDs by scanning `~/.claude/projects/**/*.jsonl` files for a unique GUID handshake marker. Includes a polling loop with configurable timeout and a `GuidScanner` seam for test injection. 7 BDD unit tests all pass. The implementation is logically sound and the architecture is clean for a V1 feature.

## Issues Found

### [MINOR] Dead Code Branch: `matchingFiles.size == 0` Is Unreachable

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`, line 114

`pollUntilFound` only returns when `matches.isNotEmpty()` is true (line 147–148). This means `matchingFiles` will always have size >= 1 when the `when` block on line 113 is evaluated. The `0 ->` branch is dead code and can never execute. The timeout path throws `IllegalStateException` and exits before reaching the `when` block.

```kotlin
return when (matchingFiles.size) {
    0 -> throw IllegalStateException(  // <-- unreachable; pollUntilFound only returns non-empty
        "No JSONL file contains GUID [$guid]"
    )
    1 -> { ... }
    else -> { ... }
}
```

This is a misleading read — a future maintainer may believe the zero-size case is reachable and work around it rather than fixing `pollUntilFound`. The `when` block should be replaced with a check for `size == 1` vs `> 1` (or `single()` with a guard), removing the dead branch.

---

### [MINOR] `private var guidScanner` — Mutation-After-Construction Pattern

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`, lines 77–94

The secondary (internal) constructor delegates to the primary constructor, which initializes `guidScanner = FilesystemGuidScanner(claudeProjectsDir)`. The secondary constructor body then immediately overwrites `guidScanner` with the injected one. This requires `guidScanner` to be `var` rather than `val`.

```kotlin
// Primary ctor sets this:
private var guidScanner: GuidScanner = FilesystemGuidScanner(claudeProjectsDir)

// Internal ctor body overwrites it:
this.guidScanner = guidScanner
```

The result: the primary constructor always eagerly creates a `FilesystemGuidScanner(PLACEHOLDER_PATH)` object even when the internal test constructor is used, which is immediately thrown away. While safe and functionally correct, the `var` on a single-assignment field is a maintainability trap — nothing prevents future code from re-assigning `guidScanner` mid-lifecycle. A follow-up refactor worth considering: move `claudeProjectsDir` to a nullable sentinel or accept `GuidScanner` directly as a private constructor parameter (making the public API take a `Path` and construct it internally). That would allow `val` and avoid the wasteful intermediate object.

---

### [MINOR] Unused Import: `kotlin.time.Duration.Companion.seconds`

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`, line 18

```kotlin
import kotlin.time.Duration.Companion.seconds  // never used; only .milliseconds is used
```

This is left over from an earlier draft. Not a functional issue but it's noise.

---

### [MINOR] `GuidScanner` Is Public but Intended Only for Test Injection

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`, line 28

`GuidScanner` has no visibility modifier, making it `public`. Its stated purpose in the KDoc is test injection. Since the test constructor using it is `internal`, the interface should be `internal` too. A public `GuidScanner` widens the surface area of the module's API without a clear external use case.

---

## No Blocking Issues

The core logic — polling loop with timeout, GUID match detection, `Files.walk` + `readText` scan, and `IllegalStateException` error propagation — is correct. The `withContext(Dispatchers.IO)` wrapping of blocking I/O is appropriate. Resource cleanup via `.use {}` on the `Files.walk` stream is correct. Tests are well-structured and cover all key scenarios including polling behavior, ambiguous matches, timeout, and non-JSONL filtering.

The `readText()` per-file approach will read entire JSONL files into memory. For long Claude Code sessions these can be large. This is acceptable for V1 where the lookup happens once at session start, not continuously. If this becomes a bottleneck, switching to `BufferedReader.lineSequence().any { it.contains(guid) }` would be the right incremental fix.
