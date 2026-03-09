# Implementation Review: Wingman Session ID Tracker

## Summary

The implementation creates a `Wingman` interface and `ClaudeCodeWingman` implementation for resolving Claude Code session IDs from GUID markers in JSONL files. The code is clean, well-structured, and follows codebase conventions. All 6 tests pass, the full build succeeds, and sanity_check.sh passes.

**Overall assessment**: Good implementation with two substantive issues that should be addressed.

---

## IMPORTANT Issues

### 1. Missing `withContext(Dispatchers.IO)` for blocking file I/O

**Severity**: IMPORTANT
**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`, line 37-44

The plan explicitly called for wrapping blocking I/O in `withContext(Dispatchers.IO)` (Phase 2, key step 3; Section 4 "File I/O and Coroutines"; Acceptance criteria checklist item). The implementation omits this entirely.

`Files.walk()`, `readText()`, and `Files.isRegularFile()` are all blocking I/O operations. Every other blocking I/O call in this codebase wraps with `Dispatchers.IO`:
- `TmuxCommandRunner.run()` at line 21
- `InteractiveProcessRunner` at line 84
- `GLMHighestTierApi.call()` at line 68

The method is `suspend` but performs blocking I/O on the caller's dispatcher, which could block the coroutine dispatcher thread pool.

**Fix**:
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// In resolveSessionId:
val matchingFiles = withContext(Dispatchers.IO) {
    Files.walk(claudeProjectsDir)
        .use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.extension == "jsonl" }
                .filter { it.readText().contains(guid) }
                .toList()
        }
}
```

### 2. `@AnchorPoint` on the interface instead of the implementation class

**Severity**: IMPORTANT
**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt`, line 14

The plan explicitly stated (Phase 1, step 4): "The `@AnchorPoint` annotation goes on the **implementation class**, not the interface (consistent with `TmuxCommunicator.kt` where interface is plain and impl has `@AnchorPoint`)."

The existing codebase pattern confirms this -- `TmuxCommunicator` interface has no `@AnchorPoint`, while `TmuxCommunicatorImpl` class has `@AnchorPoint("ap.3BCYPiR792a2B8I9ZONDwmvN.E")`.

The current implementation puts `@AnchorPoint` on the `Wingman` interface and has NO anchor point on `ClaudeCodeWingman`.

**Fix**: Move `@AnchorPoint("ap.gCgRdmWd9eTGXPbHJvyxI.E")` from `Wingman` interface to `ClaudeCodeWingman` class. Keep the `ref.ap.7sZveqPcid5z1ntmLs27UqN6.E` cross-reference in the Wingman KDoc (that is correct).

---

## Suggestions

### 3. Missing test cases from plan: empty directory and non-JSONL file filtering

**Severity**: SUGGESTION

The plan called for 6 test scenarios (Phase 3 test structure). The implementation has 6 test cases, but two from the plan are missing:
- "AND the directory contains non-JSONL files with the GUID" -- verifies `.jsonl` extension filtering works
- "AND the projects directory is empty" -- verifies behavior on empty directory

These were replaced by two tests that verify exception message content (GUID in message, "Ambiguous" in message), which are valuable. But the non-JSONL filtering test is arguably more important because it validates a code branch (the `.filter { it.extension == "jsonl" }` line) that no existing test exercises.

### 4. Test DRY: repeated tempDir setup/teardown boilerplate

**Severity**: SUGGESTION

Every `it` block creates a temp directory, wraps in try/finally, and deletes. This is significant boilerplate repetition across 6 tests. Consider extracting a helper similar to the `withFixture` pattern in `GLMHighestTierApiTest`:

```kotlin
private suspend fun withTempDir(block: suspend (Path) -> Unit) {
    val tempDir = createTempDirectory("wingman-test-")
    try {
        block(tempDir)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
```

This would reduce each test body by ~4 lines and remove a class of potential mistakes (forgetting cleanup).

### 5. Missing anchor point cross-reference in design ticket

**Severity**: SUGGESTION

The plan (Phase 4, step 3) called for adding `ap.gCgRdmWd9eTGXPbHJvyxI.E` below `## Session ID Tracking -- Wingman` in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`. The diff shows no ticket files were modified.

---

## What Looks Good

- **Interface design**: Clean contract with proper KDoc, suspend function, and clear throws documentation.
- **Implementation**: `Files.walk().use {}` properly closes the stream. The `when` on `matchingFiles.size` with exhaustive branches is clean.
- **Structured logging**: Correct use of `Val`/`ValType` for structured values. Lazy debug lambda avoids overhead.
- **Error messages**: Actionable -- includes GUID, directory, and matched filenames as appropriate.
- **Constructor injection**: No DI framework, Path injected for testability.
- **Test structure**: BDD with GIVEN/WHEN/THEN. One assert per `it`. Inherits `outFactory` from `AsgardDescribeSpec`.
- **No existing tests broken**: Full build passes, sanity check passes.

---

## Verdict

Two IMPORTANT issues to fix (Dispatchers.IO, AnchorPoint placement). Neither requires architectural changes -- both are small, localized fixes. Suggestions are optional improvements.

**Recommendation**: Fix the two IMPORTANT issues, then the implementation is ready to merge. IMPLEMENTATION_ITERATION should address these two items.
