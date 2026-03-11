# Detailed Implementation Plan: Shared App Dependencies Integration Spec

Ticket: `nid_q1s3us9fsufo5uglosuthzkr4_E` — "Create shared app dependencies integ spec"
Date: 2026-03-11

---

## 1. Problem Understanding

### Goal

Eliminate inline dependency construction from integration tests by introducing:
1. `SharedAppDepIntegFactory` — a static singleton providing a shared `Initializer`, `AppDependencies`, and `TestOutManager` for integration tests.
2. `SharedAppDepSpecConfig` — a `data class` wrapping `AsgardDescribeSpecConfig` with defaults pulled from `SharedAppDepIntegFactory`.
3. `SharedAppDepDescribeSpec` — an abstract base class extending `AsgardDescribeSpec` that integration tests extend instead of `AsgardDescribeSpec` directly, with no config boilerplate needed.
4. `Initializer.initialize()` refactored to accept `OutFactory` as a parameter instead of creating `SimpleConsoleOutFactory` internally.

### Constraints

- `AsgardDescribeSpec` constructor: `abstract class AsgardDescribeSpec(body: AsgardDescribeSpec.() -> Unit = {}, config: AsgardDescribeSpecConfig = AsgardDescribeSpecConfig())`. The config must be passed at construction time.
- `AppDependencies` implements `AsgardCloseable`. Shared instance must NOT be closed mid-test-suite. Lifecycle is process-scoped (JVM test process).
- `initialize()` is `suspend` — must be invoked with `runBlocking` at process startup (acceptable per standards at main/test entry points).
- `SharedAppDepIntegFactory` is test-only infrastructure (lives in `src/test/kotlin`).
- No DI framework. All wiring is explicit constructor injection.
- `AppMain.kt` calls `Initializer.standard().initialize()` with no `OutFactory` argument today — must be updated.

### Key Assumption

The `AppDependencies` singleton in `SharedAppDepIntegFactory` is intentionally NOT closed between tests. It is a process-scoped resource valid for the entire test suite lifetime. This is acceptable because `AsgardCloseable.close()` is only needed to prevent resource leaks in long-running servers — not in a test JVM that exits after the suite.

---

## 2. High-Level Architecture

```
SharedAppDepIntegFactory (companion object, src/test)
  ├── testOutManager: TestOutManager  [val, initialized at class load time]
  ├── appDependencies: AppDependencies  [val, initialized via runBlocking at class load time]
  └── methods:
       ├── getTestOutManager(): TestOutManager
       ├── getAppDependencies(): AppDependencies
       └── buildDescribeSpecConfig(): AsgardDescribeSpecConfig  [returns FOR_INTEG_TEST variant with shared testOutManager]

SharedAppDepSpecConfig (data class, src/test)
  └── asgardConfig: AsgardDescribeSpecConfig = AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(
        testOutManager = SharedAppDepIntegFactory.getTestOutManager()
      )

SharedAppDepDescribeSpec (abstract class, src/test)
  extends AsgardDescribeSpec(body, config = SharedAppDepSpecConfig().asgardConfig)
  └── anchor_point on class: ap.20lFzpGIVAbuIXO5tUTBg.E
       KDoc: explains pattern, when to use, references anchor point

Initializer (interface, src/main)
  └── initialize(outFactory: OutFactory, environment: Environment): AppDependencies

AppMain.kt
  └── Initializer.standard().initialize(outFactory = SimpleConsoleOutFactory.standard())

SpawnTmuxAgentSessionUseCaseIntegTest
  └── extends SharedAppDepDescribeSpec (removes inline construction)
```

---

## 3. Implementation Phases

### Phase 1: Refactor `Initializer.initialize()` to Accept `OutFactory`

**Goal:** Decouple `InitializerImpl` from `SimpleConsoleOutFactory`, allowing callers to inject any `OutFactory` (including `TestOutManager.outFactory` in tests).

**Files affected:**
- `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt`

**Key Steps:**

1. Change `Initializer` interface signature:
   ```kotlin
   interface Initializer {
       suspend fun initialize(
           outFactory: OutFactory,
           environment: Environment = Environment.production()
       ): AppDependencies
   ```
   Remove the default value from `environment` if desired, but keep it consistent with existing usage. Keep default `Environment.production()`.

2. Update `InitializerImpl.initialize()`:
   - Remove `val outFactory = SimpleConsoleOutFactory.standard()` (line 60).
   - Accept `outFactory: OutFactory` as first parameter.
   - Use the injected `outFactory` directly.
   - The private `initializeImpl(outFactory, environment)` call is unchanged — it already takes `OutFactory`.

3. Update `initializeImpl` signature: The parameter type can be widened from `SimpleConsoleOutFactory` to `OutFactory` since it is passed downstream (no `SimpleConsoleOutFactory`-specific API is used inside).

4. Update `AppMain.kt` call site:
   ```kotlin
   val deps = Initializer.standard().initialize(
       outFactory = SimpleConsoleOutFactory.standard()
   )
   ```
   The import `com.asgard.core.out.impl.console.SimpleConsoleOutFactory` is already present in `Initializer.kt` (remove it from there, add to `AppMain.kt`).

**Dependencies:** None — this phase is self-contained.

**Verification:**
- `./gradlew :app:build` passes (compile-time check that all callers are updated).
- `AppMain.kt` compiles with explicit `outFactory` argument.

---

### Phase 2: Create `SharedAppDepIntegFactory`

**Goal:** Provide a single, shared `AppDependencies` and `TestOutManager` for all integration tests.

**File location:** `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepIntegFactory.kt`

**Package:** `com.glassthought.chainsaw.integtest`

**Key Design Decision — Initialization Timing:**

Use `object` (Kotlin singleton object) with `val` properties initialized eagerly at class-load time via `runBlocking`. This is the simplest approach: no lazy/mutex complexity, initialization happens once when the class is first referenced, `runBlocking` is acceptable at this non-production entry point.

**Key Steps:**

1. Create `object SharedAppDepIntegFactory` with companion-like static access (since Kotlin `object` already provides static-style access, no `companion object` wrapper is needed inside it).

2. Initialize `testOutManager` as a `val` using `TestOutManager.standard()` (no suspension needed).

3. Initialize `appDependencies` as a `val` using `runBlocking { Initializer.standard().initialize(outFactory = testOutManager.outFactory, environment = Environment.test()) }`. This runs at JVM class-load time for the test suite.

4. Provide accessor methods (for consistency and to allow KDoc):
   - `fun getTestOutManager(): TestOutManager`
   - `fun getAppDependencies(): AppDependencies`
   - `fun buildDescribeSpecConfig(): AsgardDescribeSpecConfig` — returns `AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(testOutManager = testOutManager)`

5. Add KDoc explaining the purpose and lifecycle of the shared instance.

**Note on `AsgardDescribeSpecConfig.FOR_INTEG_TEST`:** This preset already has `shouldStopOnFirstFailure = true`, `overrideLogLevelProvider = LogLevelProvider.DEBUG`, and `afterTestLogLevelVerifyConfig = VerifyNoLinesAtOrAbove(LogLevel.DATA_ERROR)` — exactly right for integration tests. Use `.copy(testOutManager = ...)` to inject the shared `TestOutManager`.

**Dependencies:** Phase 1 must be complete (for the updated `initialize` signature).

**Verification:**
- File compiles as part of `./gradlew :app:test --dry-run`.

---

### Phase 3: Create `SharedAppDepSpecConfig` and `SharedAppDepDescribeSpec`

**Goal:** Provide the base class that integration tests extend, removing all config boilerplate.

**File locations:**
- `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt`

Both `SharedAppDepSpecConfig` and `SharedAppDepDescribeSpec` can live in the same file since `SharedAppDepSpecConfig` is tightly coupled to `SharedAppDepDescribeSpec`.

**Package:** `com.glassthought.chainsaw.integtest`

**Key Steps:**

1. Create `data class SharedAppDepSpecConfig`:
   ```kotlin
   data class SharedAppDepSpecConfig(
       val asgardConfig: AsgardDescribeSpecConfig =
           SharedAppDepIntegFactory.buildDescribeSpecConfig()
   )
   ```
   This is deliberately minimal — it wraps the `AsgardDescribeSpecConfig` with the shared factory defaults, and the `asgardConfig` field is what gets passed to `AsgardDescribeSpec`.

2. Create `abstract class SharedAppDepDescribeSpec`:
   - Constructor: `(body: AsgardDescribeSpec.() -> Unit, config: SharedAppDepSpecConfig = SharedAppDepSpecConfig())`
   - Delegates to: `AsgardDescribeSpec(body, config.asgardConfig)`
   - Annotate with `@AnchorPoint("ap.20lFzpGIVAbuIXO5tUTBg.E")`
   - Expose `val appDependencies: AppDependencies` via `SharedAppDepIntegFactory.getAppDependencies()`

3. Write comprehensive KDoc on `SharedAppDepDescribeSpec` covering:
   - What it is (base class for integration tests with shared app dependencies)
   - When to use it vs plain `AsgardDescribeSpec` (use this when you need `AppDependencies`; use plain `AsgardDescribeSpec` for unit tests)
   - Usage example showing the class declaration and how to access `appDependencies`
   - Notes about the shared singleton lifecycle
   - Reference: `ap.20lFzpGIVAbuIXO5tUTBg.E`

**AsgardDescribeSpec Constructor Compatibility:**

`AsgardDescribeSpec` signature is:
```kotlin
abstract class AsgardDescribeSpec(
    body: AsgardDescribeSpec.() -> Unit = {},
    private val config: AsgardDescribeSpecConfig = AsgardDescribeSpecConfig(),
)
```

`SharedAppDepDescribeSpec` must pass `config.asgardConfig` as the second argument. Example:
```kotlin
abstract class SharedAppDepDescribeSpec(
    body: AsgardDescribeSpec.() -> Unit,
    config: SharedAppDepSpecConfig = SharedAppDepSpecConfig(),
) : AsgardDescribeSpec(body, config.asgardConfig) {
    val appDependencies: AppDependencies
        get() = SharedAppDepIntegFactory.getAppDependencies()
}
```

**Dependencies:** Phase 2 must be complete.

**Verification:**
- File compiles.
- `SharedAppDepDescribeSpec` can be extended by a test class without providing any config.

---

### Phase 4: Update `SpawnTmuxAgentSessionUseCaseIntegTest`

**Goal:** Replace inline dependency construction with `SharedAppDepDescribeSpec` and `SharedAppDepIntegFactory`.

**File:** `app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt`

**Key Steps:**

1. Change class declaration from `AsgardDescribeSpec` to `SharedAppDepDescribeSpec`.

2. Remove all inline dependency construction (lines 36-57):
   - `TmuxCommandRunner()`
   - `TmuxCommunicatorImpl(...)`
   - `TmuxSessionManager(...)`
   - `ClaudeCodeAgentStarterBundleFactory(...)`
   - `DefaultAgentTypeChooser()`
   - `SpawnTmuxAgentSessionUseCase(...)`

3. Pull from `appDependencies` (inherited from `SharedAppDepDescribeSpec`):
   - `appDependencies.tmuxSessionManager` for session management
   - `appDependencies.outFactory` for logging (note: `outFactory` is also inherited from `AsgardDescribeSpec` directly — use that for test logging; `appDependencies.outFactory` is the same instance)
   - `appDependencies.tmuxCommandRunner` for the `ClaudeCodeAgentStarterBundleFactory`

4. Reconstruct the objects that are test-specific (not part of `AppDependencies`):
   - `ClaudeCodeAgentStarterBundleFactory` — still constructed inline with `appDependencies.outFactory`
   - `DefaultAgentTypeChooser` — still constructed inline
   - `SpawnTmuxAgentSessionUseCase` — still constructed inline using above + `appDependencies.tmuxSessionManager`

   These are use-case-specific wiring, not app-level shared dependencies, so they stay inline.

5. Remove import of `Environment` (no longer used in this file).

6. Keep `resolveSystemPromptFilePath()` and `findGitRepoRoot()` helper functions — they remain necessary.

7. Remove imports that are no longer needed (`TmuxCommandRunner`, `TmuxCommunicatorImpl`, `TmuxSessionManager` if they were only used for inline construction — check: `TmuxSessionManager` is still needed for `afterEach { sessionManager.killSession(...) }`).

   Actually: `sessionManager` is used in `afterEach`. Since `appDependencies.tmuxSessionManager` is accessible, reference it as `appDependencies.tmuxSessionManager` in `afterEach`, or assign to a local `val sessionManager = appDependencies.tmuxSessionManager` in the describe block.

8. Update imports: add `com.glassthought.chainsaw.integtest.SharedAppDepDescribeSpec`.

**Multiple Assertions in Existing Test:**
The existing `it` block has multiple assertions (lines 84-91). This is noted in the exploration report as a deviation from one-assert-per-test. The ticket does NOT require fixing this — only the refactoring. Leave the existing test structure intact. Do NOT split assertions unless specifically asked.

**Dependencies:** Phase 3 must be complete.

**Verification:**
- Test still compiles.
- Running `./gradlew :app:test -PrunIntegTests=true` (with tmux available) passes.

---

### Phase 5: Update Auto-Load Memory

**Goal:** Guide future integration test authors to use `SharedAppDepDescribeSpec`.

**File to update:** `ai_input/memory/auto_load/4_testing_standards.md`

**Key Steps:**

1. Add a new section "Integration Test Base Class" after the existing "Integration Tests (environment-dependent)" section:

   ```markdown
   ### Integration Test Base Class (with AppDependencies)
   - For integration tests requiring `AppDependencies` (tmux, LLM, etc.), extend `SharedAppDepDescribeSpec` instead of `AsgardDescribeSpec` directly.
   - `SharedAppDepDescribeSpec` provides a shared singleton `AppDependencies` and pre-configured `AsgardDescribeSpecConfig.FOR_INTEG_TEST` settings.
   - No config required — defaults pull from `SharedAppDepIntegFactory`.
   - Access shared deps via `appDependencies` property (e.g., `appDependencies.tmuxSessionManager`).
   - See `SharedAppDepDescribeSpec` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) for full KDoc and examples.
   - Location: `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt`
   ```

**Dependencies:** Phase 3 (anchor point must exist on `SharedAppDepDescribeSpec`).

**Verification:**
- Memory file is syntactically valid Markdown.
- Anchor point reference is correct.

---

## 4. Technical Considerations

### `initializeImpl` Parameter Type Widening

`initializeImpl` currently takes `SimpleConsoleOutFactory` (the concrete type). After the refactor, it should accept `OutFactory` (the interface). This is a safe widening — all downstream constructors (`TmuxCommunicatorImpl`, `TmuxSessionManager`, `GLMHighestTierApi`) already accept `OutFactory`.

### `runBlocking` in `SharedAppDepIntegFactory`

Using `runBlocking` at class-load time (as a property initializer) is the standard approach for this pattern in Kotlin test infrastructure. It satisfies the "acceptable at main entry points and tests" rule in `3_kotlin_standards.md`. The alternative (lazy + mutex) adds complexity for no benefit here.

### `AppDependencies` Lifecycle in Tests

The shared `AppDependencies` is NOT closed between tests. It is held for the JVM process lifetime. This is intentional:
- Tests share the same `OkHttpClient` connection pool (efficient).
- Tests share the same `TestOutManager` (consistent log capture).
- The JVM test process will exit after the suite, releasing all resources via OS cleanup.

If a future test needs isolated dependencies, it should construct its own `AppDependencies` locally (as `AppDependenciesCloseTest` does today).

### `AsgardDescribeSpecConfig.FOR_INTEG_TEST` vs Custom Config

`FOR_INTEG_TEST` preset has:
- `shouldStopOnFirstFailure = true` — appropriate for integ tests (see KDoc in `AsgardDescribeSpecConfig`)
- `overrideLogLevelProvider = LogLevelProvider.DEBUG` — verbose logging for debugging
- `afterTestLogLevelVerifyConfig = VerifyNoLinesAtOrAbove(LogLevel.DATA_ERROR)`

This is the right baseline for integration tests. `SharedAppDepSpecConfig` uses `.copy(testOutManager = SharedAppDepIntegFactory.getTestOutManager())` to inject the shared `TestOutManager` while keeping all other `FOR_INTEG_TEST` settings.

### Import of `SimpleConsoleOutFactory` in `AppMain.kt`

`AppMain.kt` currently does NOT import `SimpleConsoleOutFactory` (it delegates to `Initializer.standard().initialize()` with no args). After the refactor, `AppMain.kt` must import and instantiate `SimpleConsoleOutFactory.standard()` to pass as the `outFactory` argument.

---

## 5. File Locations Summary

| File | Action | Package |
|------|--------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt` | Modify: add `outFactory` param | `com.glassthought.chainsaw.core.initializer` |
| `app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt` | Modify: pass `SimpleConsoleOutFactory.standard()` | `com.glassthought.chainsaw.cli` |
| `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepIntegFactory.kt` | Create new | `com.glassthought.chainsaw.integtest` |
| `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt` | Create new (contains both `SharedAppDepSpecConfig` and `SharedAppDepDescribeSpec`) | `com.glassthought.chainsaw.integtest` |
| `app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt` | Modify: extend `SharedAppDepDescribeSpec` | `org.example` |
| `ai_input/memory/auto_load/4_testing_standards.md` | Modify: add section on `SharedAppDepDescribeSpec` | N/A |

---

## 6. Testing Strategy

### No new unit tests for factory infrastructure
`SharedAppDepIntegFactory` and `SharedAppDepDescribeSpec` are test infrastructure themselves. Writing unit tests for test infrastructure is low-ROI. The correctness is verified by the existing integration tests continuing to pass after the refactor.

### Compile-time verification
The most important check is that `./gradlew :app:build` passes — this confirms all callers of `Initializer.initialize()` were updated.

### Integration test pass
`SpawnTmuxAgentSessionUseCaseIntegTest` after the refactor should produce identical behavior. Run with `./gradlew :app:test -PrunIntegTests=true` to confirm.

### Edge cases
- If `SharedAppDepIntegFactory` initialization fails (e.g., missing env vars for LLM API), the exception propagates at class-load time and all tests using `SharedAppDepDescribeSpec` will fail with a clear error. This is correct "fail hard" behavior.

---

## 7. Open Questions / Decisions Needed

### Q1: Should `SharedAppDepDescribeSpec` expose `appDependencies` as `protected` or `val`?

The exploration suggests `protected`. However, since this is test infrastructure (not production code), `val` (public) is simpler and allows helper functions in describe blocks to access it without special scoping. Recommend: `val` (public) for simplicity.

### Q2: Should `SharedAppDepSpecConfig` be in the same file as `SharedAppDepDescribeSpec`?

Recommendation: YES — put both in `SharedAppDepDescribeSpec.kt`. The config class is purely a support type for the describe spec and has no independent users. Keeping them co-located reduces file count.

### Q3: Should the `integtest` package be under `com.glassthought.chainsaw` or left in `org.example`?

The ticket specifies `app/src/test/kotlin/com/glassthought/chainsaw/integtest/` which aligns with the production package hierarchy. This is cleaner. The existing `org.example` integration tests can import from the new package. Recommendation: use `com.glassthought.chainsaw.integtest` as specified.

### Q4: Should `ClaudeCodeAgentStarterBundleFactory` and `SpawnTmuxAgentSessionUseCase` construction stay inline in the test?

YES — these are use-case-specific wiring that is intentionally part of this test's setup. `AppDependencies` holds only app-level shared resources. The use-case objects are test-scope concerns. This is the correct separation.

---

## 8. Anchor Point

The `SharedAppDepDescribeSpec` class carries anchor point `ap.20lFzpGIVAbuIXO5tUTBg.E`.

All references in memory files and KDocs should use `ref.ap.20lFzpGIVAbuIXO5tUTBg.E`.
