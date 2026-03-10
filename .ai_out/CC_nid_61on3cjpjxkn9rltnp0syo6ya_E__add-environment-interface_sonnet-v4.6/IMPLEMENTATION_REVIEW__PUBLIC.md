# Implementation Review: add Environment interface

**VERDICT: PASS**

---

## Summary

Three files changed: a new `Environment.kt` interface + two concrete implementations, a modified `Initializer.kt` that threads the environment parameter through its call chain, and a new `EnvironmentTest.kt` with BDD-style unit tests.

Requirements are fully met. Tests pass. No security, correctness, or data-loss issues.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `environment` parameter accepted but never consumed in `initializeImpl`

`initializeImpl` receives `environment: Environment` as a parameter but does not use it. The implementation summary acknowledges this is intentional ("establishing the interface for future use"), but it violates **Principle of Least Surprise**: a parameter that has no effect is misleading to future readers and violates SRP-at-the-parameter level.

**Options (pick one):**
- Add a `// TODO: use environment to switch to test doubles` comment at the usage point inside `initializeImpl`, making the intent explicit.
- Suppress with `@Suppress("UNUSED_PARAMETER")` and a brief KDoc explaining why it is there but unused now.

The current silent no-op parameter is the weakest of the three options.

---

## Suggestions (non-blocking)

### 1. `ProductionEnvironment` and `TestEnvironment` could be `internal`

Both concrete classes are `public` by default. `ProductionEnvironment` is only ever created inside the `companion object` factory `Environment.production()`. `TestEnvironment` is instantiated directly in the test, but since it lives in the same package (`com.glassthought.chainsaw.core.initializer.data`), marking both classes `internal` would still allow test access and better hide implementation detail.

```kotlin
internal class ProductionEnvironment : Environment {
    override val isTest: Boolean = false
}

internal class TestEnvironment : Environment {
    override val isTest: Boolean = true
}
```

### 2. Sealed interface instead of open interface

Since there are exactly two known environments and both are defined in the same file, a `sealed interface` (or `sealed class`) would give exhaustive `when` checks for free — consistent with the CLAUDE.md principle "Sealed classes/enums in `when` — no `else` branch; let compiler enforce exhaustiveness." Useful once consumers start branching on environment type.

```kotlin
sealed interface Environment {
    val isTest: Boolean
    companion object { fun production(): Environment = ProductionEnvironment() }
}
```

### 3. `AppMain.kt` bypasses the `Initializer` interface

Pre-existing issue (not introduced here): `AppMain.kt` calls `InitializerImpl().initialize()` directly instead of going through `Initializer.standard().initialize()`. This is a minor inconsistency with `CallGLMApiSandboxMain.kt` which correctly uses `Initializer.standard()`. Consider a follow-up ticket.

---

## Documentation Updates Needed

None required.
