# ContextWindowStateReader Implementation

## What Was Done

Implemented the `ContextWindowStateReader` interface and its `ClaudeCodeContextWindowStateReader` implementation for reading context window state from external hook files.

### Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/.../contextwindow/ContextWindowStateReader.kt` | Interface with `read(agentSessionId)` suspend function. Carries AP `ap.ufavF1Ztk6vm74dLAgANY.E`. |
| `app/src/main/kotlin/.../contextwindow/ContextWindowStateUnavailableException.kt` | Exception for missing/malformed state files. Extends `RuntimeException`. |
| `app/src/main/kotlin/.../contextwindow/ContextWindowSlimDto.kt` | Jackson DTO for `context_window_slim.json` (snake_case via `@JsonProperty`). |
| `app/src/main/kotlin/.../contextwindow/ClaudeCodeContextWindowStateReader.kt` | Implementation reading from `${HOME}/.vintrin_env/claude_code/session/<sessionId>/context_window_slim.json`. |
| `app/src/test/kotlin/.../contextwindow/ClaudeCodeContextWindowStateReaderTest.kt` | 7 BDD test cases covering all behaviors. |

### Key Design Decisions

1. **Injectable `basePath`**: The file system root is a constructor parameter (default: `$HOME/.vintrin_env/claude_code/session`), enabling tests to use temp directories.

2. **Nullable DTO + explicit validation**: Jackson KotlinModule silently defaults missing primitive `Int` fields to 0 instead of throwing. To detect missing fields reliably, the DTO uses nullable types (`Int?`, `String?`) and the reader validates post-parse with clear error messages.

3. **`ValidatedContextWindowSlim` inner class**: After validation, a non-nullable data class carries the parsed values, avoiding null-checks downstream.

4. **Specific exception catches**: `JacksonException` and `IOException` for parse, `DateTimeParseException` for timestamp -- avoids detekt `TooGenericExceptionCaught`.

5. **`@param:JsonProperty` annotation target**: Uses `@param:` to avoid Kotlin compiler warning about future annotation target changes (KT-73255).

### Test Results

All 7 new tests pass. Full suite (607+ tests) green.

| Test Case | Result |
|-----------|--------|
| Fresh timestamp -> returns remainingPercentage | PASS |
| Stale timestamp -> returns null + WARN logged | PASS |
| File missing -> throws exception | PASS |
| Missing fields -> throws exception | PASS |
| Unparseable JSON -> throws exception | PASS |
| Invalid timestamp format -> throws exception | PASS |
| Exact stale boundary -> not stale (returns value) | PASS |
