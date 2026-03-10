# Logical Review — Wingman Session ID Tracker

**Verdict**: NOT READY

---

## Issues Found

### [SEVERITY: MAJOR] `resolveSessionId` is a one-shot scan with no retry — will always fail in real usage

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`

The full call sequence per the design doc is:

1. Harness creates TMUX session
2. Harness starts `claude` inside the session
3. Harness sends GUID via `send-keys`: `"Here is a GUID: [GUID]. We will use it to identify this session."`
4. Wingman resolves session ID from GUID

Step 3 is fire-and-forget (TMUX `send-keys`). Claude Code must **receive the message, process it, and write at least one line to its JSONL session file** before the scan can succeed. This takes several seconds at minimum (process startup alone, before any LLM response is written).

`resolveSessionId` does a single scan of the filesystem and throws `IllegalStateException` on zero matches. In the real flow, calling this immediately after `send-keys` will almost certainly produce zero matches and throw — because the JSONL file has not yet been created/populated.

The function's `suspend` modifier gives the impression it can yield and retry, but it does no such thing. The interface `Wingman` also gives no indication of polling semantics.

There are two valid designs. The polling must live somewhere, and right now it lives nowhere:

**Option A — polling inside `ClaudeCodeWingman.resolveSessionId`** (timeout + retry delay):
```kotlin
// withTimeout(2.minutes) {
//     while (true) {
//         val match = scanOnce(guid)
//         if (match != null) return@withTimeout match
//         delay(500.milliseconds)
//     }
// }
```

**Option B — polling at the call site** with `resolveSessionId` remaining a one-shot scan.
If this is the intended design, the interface contract MUST document it explicitly (e.g., `@throws IllegalStateException if GUID is not found on this scan attempt`) so callers know they must loop.

Currently neither option is implemented, and the contract is silent on retry. This is a broken design that will surface immediately in integration.

---

### [SEVERITY: MINOR] `import kotlin.streams.toList` is deprecated

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`, line 14

`kotlin.streams.toList` has been `@Deprecated(level = DeprecationLevel.WARNING)` since Kotlin 1.8. The idiomatic, non-deprecated replacement is to collect directly from the Java stream:

```kotlin
// Instead of:
import kotlin.streams.toList
stream.toList()

// Use:
stream.collect(java.util.stream.Collectors.toList())
// or
stream.toList()  // java.util.stream.Stream.toList() — available since Java 16
```

Given the project targets Java 21 (see `build.gradle.kts`), `stream.toList()` from `java.util.stream.Stream` is available without any import and is the clean replacement.

---

### [SEVERITY: MINOR] `readText()` loads entire JSONL file into memory — problematic for large session files

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`, line 47

```kotlin
.filter { it.readText().contains(guid) }
```

Claude Code JSONL session files accumulate all tool calls, outputs, and model responses for the entire session. In a long-running session these files are easily 10–50 MB. The current implementation reads every matching `.jsonl` file in full into memory before checking for the GUID.

Since this scan happens at session startup, and `~/.claude/projects/` may contain dozens of sessions from prior work, peak memory is proportional to the number of files scanned times the largest file size.

A line-by-line approach stops at the first match, avoids loading irrelevant content, and keeps memory bounded:

```kotlin
.filter { path ->
    path.toFile().bufferedReader().useLines { lines ->
        lines.any { it.contains(guid) }
    }
}
```

This is particularly important because the GUID appears in the very first line (the first TMUX message), so streaming stops almost immediately for the correct file.

---

## Summary

The tests pass and the code compiles cleanly. The unit tests correctly exercise the happy path, zero-match, ambiguous-match, nested directory, and non-JSONL filter cases. The architecture (interface + constructor-injected dir, `Dispatchers.IO` wrapping, `Files.walk().use{}`) is sound.

The blocking issue is that `resolveSessionId` has no retry/polling behavior, which makes it non-functional in production: the JSONL file will not exist at the moment the harness calls this function after `send-keys`. The fix requires a deliberate design decision (polling inside vs. polling outside) that needs to be made explicit, implemented, and tested. The existing tests all operate on pre-populated directories, which does not exercise the timing scenario at all.

The two minor issues (`kotlin.streams.toList` deprecation and `readText()` memory usage) should be fixed before shipping but are not blockers for correctness under happy-path conditions.
