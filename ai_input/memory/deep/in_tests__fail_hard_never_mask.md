---
desc: "Tests must fail explicitly. No silent fallbacks, no conditional skipping of individual tests. Only entire test classes may be toggled."
---

## Tests: Fail Hard, Never Mask

### Forbidden Patterns

**Environment detection that provides fallbacks:**
```kotlin
// BAD — masks missing environment
private val process = if (isAvailable()) getProcess() else NoOpProcess()

// BAD — silently skips when dependency missing
if (isDependencyAvailable()) { runTests() }

// BAD — catches and ignores configuration errors
try { loadConfig() } catch (e: Exception) { useDefaults() }
```

**Conditional test skipping without explicit failure:**
```kotlin
// BAD — test appears to pass when it didn't run
it("THEN should process data") {
    if (!hasDependency()) return@it  // silent skip
    // ...
}
```

### Required Patterns

**Fail immediately with clear error messages:**
```kotlin
// GOOD — guard entire describe block with enabledIf
describe("GIVEN integration environment").config(enabledIf = { isIntegTestEnabled() }) {
    describe("WHEN processing data") {
        it("THEN should succeed") {
            // test logic
        }
    }
}

// GOOD — explicit assertion for required config
it("THEN should use config value") {
    val configValue = System.getenv("CONFIG_VAR")
        ?: fail("CONFIG_VAR environment variable must be set for integration tests")
    // ...
}
```

### Rationale

Silent fallbacks cause:
- Tests that "pass" in CI but test nothing
- Hours of debugging phantom issues
- False confidence in code that was never actually validated

When configuration is wrong, we want immediate, loud failure with actionable, clear error messages.
