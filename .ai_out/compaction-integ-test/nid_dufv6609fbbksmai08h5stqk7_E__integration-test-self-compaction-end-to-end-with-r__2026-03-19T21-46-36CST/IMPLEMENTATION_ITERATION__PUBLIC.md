# Self-Compaction Integration Test - Review Iteration

## Summary

Addressed all CRITICAL and IMPORTANT review feedback items. Extracted shared test infrastructure, eliminated duplication, and fixed correctness issues.

## Changes Made

### CRITICAL Fixes

1. **Cross-test state dependency in Scenario 2 (Review #1)**: Merged the two `it` blocks into a single `it("THEN agent signals SelfCompacted and PRIVATE.md is valid")` block. Both the signal assertion and the PRIVATE.md validation now run in the same test, eliminating the cross-test state dependency.

2. **Scenario 1 clarifying comment (Review #2)**: Kept the synthetic approach (since the vintrin hook is not guaranteed in CI) but added a prominent WHY comment block explaining the rationale — the test validates path resolution and JSON parsing with a real session ID, not the external hook.

### IMPORTANT Fixes

3. **`Pair` usage replaced (Review #3)**: Introduced `private data class SpawnedEntry(val facade: AgentFacadeImpl, val handle: SpawnedAgentHandle)` to replace `Pair<AgentFacadeImpl, SpawnedAgentHandle>`.

4. **Silent fallback on missing PRIVATE.md (Review #4)**: Replaced the `if/else` fallback with a hard `check()` that fails immediately if PRIVATE.md doesn't exist after `SelfCompacted` signal.

5. **System prompt content duplication (Review #5)**: Extracted shared callback protocol text into `IntegTestCallbackProtocol` object with constants: `CORE_PROTOCOL`, `SELF_COMPACTION_PROTOCOL`, `IMPORTANT_NOTES_BASE`, `IMPORTANT_NOTES_COMPACTION`, and `BOOTSTRAP_MESSAGE`. Both test files now compose prompts from these shared parts.

6. **`ServerPortInjectingAdapter` duplication (Review #6)**: Extracted to `app/src/test/kotlin/com/glassthought/shepherd/integtest/ServerPortInjectingAdapter.kt` as a package-level `internal class`. Both `AgentFacadeImplIntegTest` and `SelfCompactionIntegTest` now use the shared class.

### Low Priority Fixes

7. **`afterEach` exception handling (Review #7)**: Changed from `catch (_: Exception)` to `catch (_: IllegalStateException)` for more specific exception handling (session already killed is the expected failure mode).

8. **Temp dir cleanup (Review #8)**: Changed `tmpDir.listFiles()?.forEach { it.delete() }` to `tmpDir.deleteRecursively()` to handle nested directories.

## New Files Created

| File | Purpose |
|------|---------|
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/ServerPortInjectingAdapter.kt` | Shared adapter extracted from both test files |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestCallbackProtocol.kt` | Shared callback protocol constants |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestHelpers.kt` | Shared test helper utilities (scripts dir, system prompt, done instruction) |

## Files Modified

| File | Changes |
|------|---------|
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/compaction/SelfCompactionIntegTest.kt` | All review feedback applied |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt` | Removed private duplicates, uses shared classes |

## Verification

- `./test.sh` passes (unit tests + detekt)
- Compilation clean with no warnings
