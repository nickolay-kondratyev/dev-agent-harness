# Planner Private Notes

Ticket: `nid_q1s3us9fsufo5uglosuthzkr4_E`
Date: 2026-03-11

---

## Key Findings from Code Exploration

### AsgardDescribeSpec Constructor (CRITICAL)

Verified from sources jar:
```kotlin
abstract class AsgardDescribeSpec(
    body: AsgardDescribeSpec.() -> Unit = {},
    private val config: AsgardDescribeSpecConfig = AsgardDescribeSpecConfig(),
) : DescribeSpec()
```

The `body` is of type `AsgardDescribeSpec.() -> Unit` — it is a lambda with receiver of `AsgardDescribeSpec`.
This means `SharedAppDepDescribeSpec` must pass the body through to super as `AsgardDescribeSpec.() -> Unit`.

### AsgardDescribeSpecConfig.FOR_INTEG_TEST (IMPORTANT)

There is a pre-built config preset in `AsgardDescribeSpecConfig.FOR_INTEG_TEST`:
```kotlin
val FOR_INTEG_TEST = AsgardDescribeSpecConfig(
    shouldStopOnFirstFailure = true,
    overrideLogLevelProvider = LogLevelProvider.DEBUG,
    afterTestLogLevelVerifyConfig = AfterTestLogLevelVerifyConfig.VerifyNoLinesAtOrAbove(LogLevel.DATA_ERROR),
)
```
The `testOutManager` field defaults to `TestOutManagerStatic.getInstance()`. We need to `.copy(testOutManager = SharedAppDepIntegFactory.getTestOutManager())` to inject our shared instance.

### InitializerImpl.initializeImpl Parameter Type (IMPORTANT)

Current signature:
```kotlin
private fun initializeImpl(outFactory: SimpleConsoleOutFactory, environment: Environment): AppDependencies
```

The parameter type `SimpleConsoleOutFactory` must be widened to `OutFactory`. Check that no `SimpleConsoleOutFactory`-specific methods are called in `initializeImpl` — from reading the code, all downstream calls use `OutFactory` interface methods, so this is safe.

### AppMain.kt Current Call (IMPORTANT)

```kotlin
val deps = Initializer.standard().initialize()
```

After Phase 1, this must become:
```kotlin
val deps = Initializer.standard().initialize(outFactory = SimpleConsoleOutFactory.standard())
```

Import needed: `com.asgard.core.out.impl.console.SimpleConsoleOutFactory`
(This import is currently in `Initializer.kt` and must be moved/added to `AppMain.kt`.)

### SpawnTmuxAgentSessionUseCaseIntegTest - What Stays Inline

After refactor, these must still be constructed inline (they are NOT in AppDependencies):
- `ClaudeCodeAgentStarterBundleFactory` (needs `systemPromptFilePath` and `claudeProjectsDir`)
- `DefaultAgentTypeChooser`
- `SpawnTmuxAgentSessionUseCase`

What comes from `appDependencies`:
- `appDependencies.tmuxSessionManager` (for the use case AND afterEach cleanup)
- `appDependencies.outFactory` (for `ClaudeCodeAgentStarterBundleFactory` and the `out` logger)

The `outFactory` property inherited from `AsgardDescribeSpec` IS the same instance as `appDependencies.outFactory` (both come from `SharedAppDepIntegFactory.getTestOutManager().outFactory`). The implementation can use either reference — prefer `outFactory` (inherited) for test logging and pass `outFactory` to constructors.

### Anchor Point

Generated: `ap.20lFzpGIVAbuIXO5tUTBg.E`
Apply this as `@AnchorPoint("ap.20lFzpGIVAbuIXO5tUTBg.E")` on `SharedAppDepDescribeSpec` class.

---

## Potential Pitfalls for Implementor

### Pitfall 1: `body` lambda receiver type mismatch

When subclassing `AsgardDescribeSpec`, the `body` parameter is `AsgardDescribeSpec.() -> Unit`. If you declare `SharedAppDepDescribeSpec` as:
```kotlin
abstract class SharedAppDepDescribeSpec(
    body: AsgardDescribeSpec.() -> Unit,
    ...
) : AsgardDescribeSpec(body, config.asgardConfig)
```
This works cleanly. Do NOT try to use `SharedAppDepDescribeSpec.() -> Unit` as the body type — it creates a type mismatch.

### Pitfall 2: `runBlocking` in object initializer

In Kotlin `object`, properties are initialized in order of declaration. Using `runBlocking { ... }` as a property initializer is valid but causes the classloader to block during object initialization. This is acceptable in test infrastructure.

```kotlin
object SharedAppDepIntegFactory {
    val testOutManager: TestOutManager = TestOutManager.standard()  // no suspension needed
    val appDependencies: AppDependencies = runBlocking {
        Initializer.standard().initialize(
            outFactory = testOutManager.outFactory,
            environment = Environment.test()
        )
    }
    ...
}
```

### Pitfall 3: `SimpleConsoleOutFactory` import in `Initializer.kt`

After Phase 1, `SimpleConsoleOutFactory` is no longer used in `Initializer.kt`. Remove the import to avoid IDE warnings. Add the import to `AppMain.kt` instead.

### Pitfall 4: `initializeImpl` parameter type

Current `initializeImpl` takes `SimpleConsoleOutFactory`. After Phase 1:
```kotlin
private fun initializeImpl(outFactory: OutFactory, environment: Environment): AppDependencies
```
Verify no `SimpleConsoleOutFactory`-specific methods are called inside (they aren't — only `OutFactory` interface methods are used).

---

## Implementation Order Verification

1. Phase 1 (Initializer.kt + AppMain.kt) — compile check
2. Phase 2 (SharedAppDepIntegFactory.kt) — needs updated initialize() signature
3. Phase 3 (SharedAppDepDescribeSpec.kt + SharedAppDepSpecConfig) — needs factory
4. Phase 4 (SpawnTmuxAgentSessionUseCaseIntegTest.kt) — needs describe spec base class
5. Phase 5 (4_testing_standards.md) — documentation, no code dependency

---

## File Contents Summary for Implementor

### `SharedAppDepIntegFactory.kt` sketch

```kotlin
package com.glassthought.chainsaw.integtest

import com.asgard.core.out.impl.for_tests.testout.TestOutManager
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.chainsaw.core.initializer.AppDependencies
import com.glassthought.chainsaw.core.initializer.Initializer
import com.glassthought.chainsaw.core.initializer.data.Environment
import kotlinx.coroutines.runBlocking

/**
 * Provides shared, process-scoped [AppDependencies] and [TestOutManager] for integration tests.
 *
 * Initialized once at JVM class-load time via [runBlocking].
 * ...
 */
object SharedAppDepIntegFactory {

    val testOutManager: TestOutManager = TestOutManager.standard()

    val appDependencies: AppDependencies = runBlocking {
        Initializer.standard().initialize(
            outFactory = testOutManager.outFactory,
            environment = Environment.test(),
        )
    }

    fun getTestOutManager(): TestOutManager = testOutManager

    fun getAppDependencies(): AppDependencies = appDependencies

    fun buildDescribeSpecConfig(): AsgardDescribeSpecConfig =
        AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(testOutManager = testOutManager)
}
```

### `SharedAppDepDescribeSpec.kt` sketch

```kotlin
package com.glassthought.chainsaw.integtest

import com.asgard.core.annotation.AnchorPoint
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.chainsaw.core.initializer.AppDependencies

/**
 * [ap.20lFzpGIVAbuIXO5tUTBg.E]
 *
 * Base class for integration tests requiring [AppDependencies].
 *
 * ### When to use
 * Extend this class when your integration test needs access to shared application-level
 * dependencies (tmux session manager, LLM client, etc.).
 * For pure unit tests, extend [AsgardDescribeSpec] directly.
 *
 * ### Usage
 * ```kotlin
 * @OptIn(ExperimentalKotest::class)
 * class MyIntegTest : SharedAppDepDescribeSpec({
 *     describe("GIVEN ...").config(isIntegTestEnabled()) {
 *         val sessionManager = appDependencies.tmuxSessionManager
 *         // ...
 *     }
 * })
 * ```
 *
 * ### Configuration
 * Default config uses [AsgardDescribeSpecConfig.FOR_INTEG_TEST] with the shared
 * [com.asgard.core.out.impl.for_tests.testout.TestOutManager] from [SharedAppDepIntegFactory].
 * Override by passing a custom [SharedAppDepSpecConfig] if needed.
 *
 * ### Shared Instance Lifecycle
 * [appDependencies] is a process-scoped singleton shared across all tests in the suite.
 * It is NOT closed between test classes. This is intentional — the JVM test process
 * exits after the suite, releasing all resources via OS cleanup.
 */
@AnchorPoint("ap.20lFzpGIVAbuIXO5tUTBg.E")
abstract class SharedAppDepDescribeSpec(
    body: AsgardDescribeSpec.() -> Unit,
    config: SharedAppDepSpecConfig = SharedAppDepSpecConfig(),
) : AsgardDescribeSpec(body, config.asgardConfig) {

    val appDependencies: AppDependencies
        get() = SharedAppDepIntegFactory.getAppDependencies()
}

/**
 * Configuration for [SharedAppDepDescribeSpec].
 *
 * Defaults pull from [SharedAppDepIntegFactory] so no manual configuration is needed.
 */
data class SharedAppDepSpecConfig(
    val asgardConfig: AsgardDescribeSpecConfig = SharedAppDepIntegFactory.buildDescribeSpecConfig(),
)
```

---

## Risks

- **Low risk:** The `Initializer.initialize()` signature change is straightforward. Only one production call site (`AppMain.kt`). The test call site (`SharedAppDepIntegFactory`) is new.
- **Low risk:** `FOR_INTEG_TEST.copy(testOutManager = ...)` — Kotlin data class `.copy()` is reliable.
- **Low risk:** Extending `AsgardDescribeSpec` from `SharedAppDepDescribeSpec` — the constructor delegation pattern is idiomatic Kotlin.
- **Watch:** The existing `SpawnTmuxAgentSessionUseCaseIntegTest` test has multiple assertions in a single `it` block (lines 84-91). The ticket does NOT ask us to fix this. Leave it as-is to minimize scope.
