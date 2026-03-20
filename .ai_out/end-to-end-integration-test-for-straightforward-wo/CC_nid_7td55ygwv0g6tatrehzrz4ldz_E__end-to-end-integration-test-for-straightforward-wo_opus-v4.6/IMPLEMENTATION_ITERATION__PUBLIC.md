# Implementation Iteration 2: Review Feedback Addressed

## Changes Made

### 1. MUST FIX: Added comment about `--iteration-max 1` being a no-op
Added a 3-line comment above `--iteration-max 1` explaining that the CLI param is parsed but NOT consumed downstream (DEFERRED), referencing `ref.ap.mFo35x06vJbjMQ8m7Lh4Z.E` in `ShepherdInitializer`, and noting that the actual iteration limit comes from the workflow JSON (`config/workflows/straightforward.json` -> `"iteration.max": 4`).

### 2. SHOULD FIX: Temp dir now uses `$PWD/.tmp/`
Changed from `Files.createTempDirectory("shepherd-e2e-straightforward-")` (system `/tmp/`) to `Files.createTempDirectory(dotTmpDir, "e2e-straightforward-")` where `dotTmpDir = projectRoot.resolve(".tmp")`. Parent directory is created via `Files.createDirectories()`. This follows the CLAUDE.md convention and co-locates test artifacts with the project.

### 3. SHOULD FIX: Moved `listTmuxSessions()` into companion object
Moved the private free-floating function into the `companion object` of `StraightforwardWorkflowE2EIntegTest`. This follows the Kotlin coding standard: "Disfavor non-private free-floating functions."

### 4. SHOULD FIX: `runGitInTemp` now checks exit codes
Changed `runGitInTemp` to:
- Return `Unit` instead of `Int` (callers were ignoring the return value anyway)
- Capture stderr via `ProcessBuilder.Redirect.PIPE`
- `require(exitCode == 0)` with a descriptive message including the git command and stderr output
- Test now fails hard if git setup fails instead of continuing silently with a broken repo

## Verification
- `./gradlew :app:test` passes (exit code 0)
