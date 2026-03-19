# Implementation Iteration: Address Review Feedback

## Review Items Addressed

### 1. IMPORTANT: Spec divergence -- `BuildStartCommandParams` (FIXED)

Updated `doc/use-case/SpawnTmuxAgentSessionUseCase.md` at ref.ap.A0L92SUzkG3gE0gX04ZnK.E:
- Changed `buildStartCommand(bootstrapMessage: String)` to `buildStartCommand(params: BuildStartCommandParams)`
- Added `BuildStartCommandParams` data class definition to the spec
- Added rationale paragraph explaining why per-session params belong on the method (adapter is a singleton, not per-session)

### 2. IMPORTANT: Test-injection constructor visibility (FIXED)

Made the constructor `internal` in `ClaudeCodeAdapter.kt` (line 70). This was subsequently superseded by the factory pattern refactor (see below) -- the primary constructor is now `internal`.

### 3. Optional: Eliminate mutable `var guidScanner` (ACCEPTED and IMPLEMENTED)

**Rationale for accepting:** The factory pattern eliminates three code smells simultaneously:
1. The mutable `var guidScanner` field
2. The `PLACEHOLDER_PATH` sentinel (`Path.of("/dev/null")`)
3. The throwaway `FilesystemGuidScanner` object created and immediately discarded

**What changed:**
- Primary constructor now takes `GuidScanner` directly and is `internal`
- Added `companion object fun create(...)` factory for production wiring
- Removed secondary constructor, `PLACEHOLDER_PATH`, and `@Suppress("UnusedPrivateClass")`
- `guidScanner` is now `private val` (immutable)
- Production site (`ContextInitializer.kt`) uses `ClaudeCodeAdapter.create(...)`
- Tests using `claudeProjectsDir` switched to `ClaudeCodeAdapter.create(...)`
- Tests using fake `GuidScanner` continue using the `internal` primary constructor

**Complexity assessment:** Added one factory method, removed one constructor + one sentinel + one suppress annotation. Net reduction in complexity.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/.../adapter/ClaudeCodeAdapter.kt` | Factory pattern, internal constructor, immutable guidScanner |
| `app/src/main/kotlin/.../initializer/ContextInitializer.kt` | `ClaudeCodeAdapter(...)` -> `ClaudeCodeAdapter.create(...)` |
| `app/src/test/kotlin/.../adapter/ClaudeCodeAdapterTest.kt` | All `claudeProjectsDir` calls -> `create(...)` |
| `doc/use-case/SpawnTmuxAgentSessionUseCase.md` | Updated interface signature + added BuildStartCommandParams |

## Test Results

`:app:test` -- BUILD SUCCESSFUL. All tests pass.
