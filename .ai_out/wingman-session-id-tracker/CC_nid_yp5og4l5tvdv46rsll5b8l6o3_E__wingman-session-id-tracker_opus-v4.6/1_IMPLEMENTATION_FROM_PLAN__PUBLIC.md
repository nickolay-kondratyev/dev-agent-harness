# Implementation: Wingman Session ID Tracker

## What Was Implemented

### Wingman Interface
**File:** `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt`
- Package: `com.glassthought.chainsaw.core.wingman`
- Interface with `suspend fun resolveSessionId(guid: String): String`
- KDoc documenting the GUID handshake pattern with `ref.ap.7sZveqPcid5z1ntmLs27UqN6.E` cross-reference to TmuxSession
- Anchor point: `ap.gCgRdmWd9eTGXPbHJvyxI.E`

### ClaudeCodeWingman Implementation
**File:** `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`
- Constructor-injected `claudeProjectsDir: Path` and `outFactory: OutFactory`
- Recursively walks directory for `*.jsonl` files using `Files.walk()`
- Uses `readText().contains(guid)` for matching (KISS -- files are small JSONL)
- Returns filename without `.jsonl` extension as session ID on exactly 1 match
- Throws `IllegalStateException` with descriptive message on 0 matches (includes GUID and directory)
- Throws `IllegalStateException` with "Ambiguous" + GUID + filenames on multiple matches
- Structured logging: `resolving_session_id` (info), `guid_search_completed` (debug), `session_id_resolved` (info)

### Unit Tests
**File:** `app/src/test/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingmanTest.kt`
- Extends `AsgardDescribeSpec`, uses inherited `outFactory`
- 6 test cases, all passing:
  1. Single JSONL with GUID -> returns session ID from filename
  2. No files contain GUID -> throws IllegalStateException
  3. No files contain GUID -> exception message contains the GUID
  4. Multiple files contain GUID -> throws IllegalStateException
  5. Multiple files contain GUID -> exception message mentions "Ambiguous"
  6. GUID in nested subdirectory -> returns correct session ID
- Each test uses `createTempDirectory` with `try/finally` cleanup

## Test Results
- **6 tests: 6 passed, 0 failed, 0 skipped**
- All existing tests continue to pass (BUILD SUCCESSFUL)

## Design Decisions
1. **`IllegalStateException`** -- consistent with existing codebase pattern (GLMHighestTierApi uses it)
2. **Constructor-injected Path** -- enables unit testing with temp dirs, follows DIP
3. **`Files.walk().use{}`** -- ensures stream is closed properly
4. **`readText().contains(guid)`** -- KISS approach; JSONL files are small enough for full reads
5. **No `build.gradle.kts` changes** -- no new dependencies needed
6. **`ValType.FILE_PATH_STRING`** for directory path, `ValType.STRING_USER_AGNOSTIC`** for GUID/session values -- semantically specific as required

## Anchor Point
- Created: `ap.gCgRdmWd9eTGXPbHJvyxI.E` on the `Wingman` interface
