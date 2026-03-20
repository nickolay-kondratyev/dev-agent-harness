# Implementation Review -- Private Notes

## Review Process
1. Read implementation summary and exploration docs
2. Reviewed full `git diff ade56e3d..4e2b10bc` (11 files, 279 insertions, 30 deletions)
3. Ran `./sanity_check.sh` -- passed
4. Ran `./test.sh` -- passed (BUILD SUCCESSFUL)
5. Traced all `callbackScriptsDir` usages in production and test code to verify type consistency
6. Verified `ClaudeCodeInfra` data class does not directly hold the path (it holds `AgentTypeAdapter`)

## Key Observations

### Correctness
- The `CallbackScriptsDir.validated()` factory performs three checks: directory exists, script exists, script is executable. This covers the root cause (exit code 127 from missing script on PATH).
- The validation happens at construction time (fail-fast), which means misconfigured PATH will be caught before any agent is spawned.
- All consuming sites are updated consistently -- no stale `String` references remain.

### Test Coverage
- `CallbackScriptsDirTest` covers: valid dir, nonexistent dir, missing script, non-executable script, file-instead-of-dir, forTest factory, equals/hashCode. Good coverage.
- Existing tests (`ClaudeCodeAdapterTest`, `TicketShepherdCreatorTest` x2, `ShepherdInitializerTest`) updated to use `forTest()`.
- `IntegTestHelpers` uses `validated()` with real filesystem -- appropriate for integ tests.

### Architecture
- Type replaces raw `String` at the API boundary -- good use of the type system.
- Private constructor + companion factory is a clean pattern for validated types.
- The `forTest` naming in production code is the only notable concern (see public review).

### No Functionality Removed
- No tests were removed or skipped.
- No anchor points were removed.
- Previous validation logic in `IntegTestHelpers` was not removed but replaced by delegation to `CallbackScriptsDir.validated()` -- equivalent behavior.

### Design Decision: Regular class vs inline value class
- Documented in the implementation summary. Justified: `@JvmInline` requires public constructor, which would break the validated factory pattern. Acceptable tradeoff.
