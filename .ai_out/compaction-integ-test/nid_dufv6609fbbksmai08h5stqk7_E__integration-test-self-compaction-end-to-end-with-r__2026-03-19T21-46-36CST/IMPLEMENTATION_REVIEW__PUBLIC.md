# Self-Compaction Integration Test - Code Review

## Summary

New file: `app/src/test/kotlin/com/glassthought/shepherd/integtest/compaction/SelfCompactionIntegTest.kt`

Adds E2E integration tests for the self-compaction flow across 3 scenarios: context window JSON readability, compaction trigger with `SelfCompacted` signal, and session rotation with PRIVATE.md. The test follows the existing `AgentFacadeImplIntegTest` pattern, extends `SharedContextDescribeSpec`, and gates correctly with `isIntegTestEnabled()`.

Build passes (`./test.sh`, `./sanity_check.sh`). No existing tests removed or modified.

Overall: solid test structure, but has two correctness issues that need fixing and one DRY violation worth addressing.

---

## CRITICAL Issues

### 1. Cross-test state dependency in Scenario 2 (lines 217-265)

The `privateMdPath` is computed once at `describe` block scope (line 219), then **two separate `it` blocks** depend on the same path:

- `it("THEN agent signals SelfCompacted")` (line 222) -- spawns agent, sends compaction, expects signal
- `it("THEN PRIVATE.md exists and is non-empty")` (line 258) -- validates PRIVATE.md at the path

The second `it` block **depends on the first having run successfully**. If the first test fails (agent doesn't write PRIVATE.md), the second test will fail with a misleading error ("agent did not produce PRIVATE.md") rather than revealing the true cause. Worse, Kotest does not guarantee `it` block ordering within a `describe`.

This violates the "one assert per test" principle in spirit -- these are not independent assertions. Each `it` block should be self-contained.

**Fix**: Merge these into a single `it` block with two assertions (they are part of the same logical scenario), OR have the second `it` block do its own spawn+compaction+validation. The single `it` approach is simpler and correct:

```kotlin
describe("WHEN self-compaction instruction is sent to agent") {
    it("THEN agent signals SelfCompacted and PRIVATE.md is valid") {
        // ... spawn, send compaction ...
        signal.shouldBeInstanceOf<AgentSignal.SelfCompacted>()

        val validator = PrivateMdValidator()
        val result = validator.validate(privateMdPath, "compaction-trigger")
        result shouldBe PrivateMdValidator.ValidationResult.Valid
    }
}
```

### 2. Scenario 1 does not add value beyond existing unit tests (lines 159-213)

Scenario 1 spawns a real agent, then **manually writes a synthetic `context_window_slim.json`** and reads it back. The agent itself is not involved in producing or consuming the file -- it merely provides a session ID. This makes the test equivalent to the existing unit test `ClaudeCodeContextWindowStateReaderTest` but with unnecessary agent-spawning overhead.

An integration test should validate something that unit tests cannot. If the purpose is "validate the real file path resolution works with a real session ID", that is borderline useful since the path is just string concatenation. If the intent is "validate the external hook actually produces the file", the test should **wait for the real file to appear** rather than writing a synthetic one.

**Recommendation**: Either (a) remove this scenario since it duplicates unit test coverage, or (b) re-scope it to actually poll for the real `context_window_slim.json` produced by the external hook (if the hook is present in the integration test environment). If neither is feasible, at minimum rename the test to clarify it only validates "path resolution with a real session ID" rather than implying end-to-end readability.

---

## IMPORTANT Issues

### 3. `ServerPortInjectingAdapter` is duplicated (lines 415-451 vs `AgentFacadeImplIntegTest` lines 238-272)

`CompactionServerPortInjectingAdapter` is a near-identical copy of `ServerPortInjectingAdapter` from `AgentFacadeImplIntegTest`. The implementation doc acknowledges this: "Duplicated from AgentFacadeImplIntegTest (private to that class)."

Both classes implement the same `AgentTypeAdapter` wrapping with identical string manipulation logic. If the command format changes, both must be updated -- a DRY violation with real maintenance cost.

**Fix**: Extract to a shared test utility, e.g.:
`app/src/test/kotlin/com/glassthought/shepherd/integtest/support/ServerPortInjectingAdapter.kt`

Make the original in `AgentFacadeImplIntegTest` use it too. This is test infrastructure, not production code, so shared helpers are appropriate.

### 4. `Pair<AgentFacadeImpl, SpawnedAgentHandle>` usage (line 137)

CLAUDE.md explicitly says: "No `Pair`/`Triple` -- create descriptive `data class`." The `spawnedEntries` list uses `Pair<AgentFacadeImpl, SpawnedAgentHandle>` for cleanup tracking.

**Fix**:
```kotlin
private data class SpawnedEntry(
    val facade: AgentFacadeImpl,
    val handle: SpawnedAgentHandle,
)
```

### 5. Scenario 3 silent fallback on missing PRIVATE.md (lines 320-324)

```kotlin
val privateMdContent = if (Files.exists(privateMdPath)) {
    Files.readString(privateMdPath)
} else {
    "No PRIVATE.md -- agent did not write the file."
}
```

If the compaction step succeeded (signal was `SelfCompacted`) but PRIVATE.md was NOT written, this code **silently proceeds** with a fallback string rather than failing hard. Per CLAUDE.md testing standards: "Tests must fail explicitly when dependencies, setup, or configuration are missing. No silent fallbacks."

The test should fail immediately if PRIVATE.md doesn't exist after a successful `SelfCompacted` signal:

```kotlin
check(Files.exists(privateMdPath)) {
    "PRIVATE.md not found at [$privateMdPath] after SelfCompacted signal"
}
val privateMdContent = Files.readString(privateMdPath)
```

### 6. System prompt content duplication

`createCompactionSystemPromptFile()` and `createRotatedSystemPromptFile()` share ~80% identical content (callback protocol section). The callback protocol instructions are copy-pasted between the two methods and also largely duplicated from `IntegTestHelpers.createIntegTestSystemPromptFile()` in the existing test.

**Fix**: Extract the shared callback protocol text into a constant or helper method and compose the system prompts from reusable parts.

---

## Suggestions

### 7. `afterEach` cleanup catches and swallows all exceptions (lines 139-148)

The `catch (_: Exception)` silently swallows cleanup failures. While the comment says "Session may already be killed," this masks any unexpected cleanup errors. Consider logging a warning or catching a more specific exception type.

### 8. `tmpDir.listFiles()?.forEach { it.delete() }` in `afterSpec` (line 156)

This only deletes direct children of `tmpDir`, not subdirectories or their contents. If any test creates nested structures, cleanup will be incomplete. Consider using `tmpDir.deleteRecursively()`.

### 9. Consider using `beforeSpec` + `afterSpec` for server lifecycle

The Ktor server is started eagerly in the `describe` block body (line 80-82). If any test construction fails between server start and `afterSpec` registration, the server won't be stopped. This is a minor risk since Kotest processes `afterSpec` even if the describe body throws, but it is worth noting.

---

## Documentation Updates Needed

None required. The implementation doc at `1_IMPLEMENTATION_WITH_SELF_PLAN__PUBLIC.md` accurately describes what was built. CLAUDE.md does not need updates.
