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
@Test fun integTest() {
    if (!hasDependency()) return  // silent skip
    // ...
}
```

### Required Patterns

**Fail immediately with clear error messages:**
```kotlin
// GOOD — fail in test setup, not silently skip
@BeforeAll fun checkDependencies() {
    require(isDependencyOnPath()) {
        "Integration tests require 'dependency' on PATH. Current PATH: ${System.getenv("PATH")}"
    }
}

// GOOD — explicit assertion for required config
@Test fun integTest() {
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
