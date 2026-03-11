# Exploration Report: Shared App Dependencies Integration Test Spec

Date: 2026-03-11

## Overview

This exploration provides comprehensive analysis of the codebase to support implementation of the ticket: "Create shared app dependencies integ spec" (nid_q1s3us9fsufo5uglosuthzkr4_E).

The goal is to:
1. Create `SharedAppDepIntegFactory` — a shared factory for initializing AppDependencies across integration tests
2. Create `SharedAppDepDescribeSpec` — a reusable base class for integration tests extending AsgardDescribeSpec
3. Refactor `Initializer.initialize()` to accept an OutFactory parameter instead of creating SimpleConsoleOutFactory internally
4. Update auto_load memory to guide future integration tests toward this pattern

---

## 1. Current State of SpawnTmuxAgentSessionUseCaseIntegTest.kt

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt`

### Key Characteristics

- **Extends:** `AsgardDescribeSpec` directly (lines 33)
- **Gating:** Uses `isIntegTestEnabled()` function on describe block (line 35)
- **Initialization Pattern:** Inline construction of all dependencies within the test

### Inline Dependency Construction (lines 36-57)

```kotlin
val commandRunner = TmuxCommandRunner()
val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

val systemPromptFilePath = resolveSystemPromptFilePath()
val out = outFactory.getOutForClass(SpawnTmuxAgentSessionUseCaseIntegTest::class)

val bundleFactory: AgentStarterBundleFactory = ClaudeCodeAgentStarterBundleFactory(
    environment = Environment.test(),
    systemPromptFilePath = systemPromptFilePath,
    claudeProjectsDir = Path.of(System.getProperty("user.home"), ".claude", "projects"),
    outFactory = outFactory,
)

val agentTypeChooser: AgentTypeChooser = DefaultAgentTypeChooser()

val useCase = SpawnTmuxAgentSessionUseCase(
    agentTypeChooser = agentTypeChooser,
    bundleFactory = bundleFactory,
    tmuxSessionManager = sessionManager,
    outFactory = outFactory,
)
```

### Test Structure

- **describe block:** "GIVEN SpawnTmuxAgentSessionUseCase with test configuration"
- **Session cleanup:** `afterEach` block (lines 61-70) kills all created tmux sessions
- **Single test case:** Only one `it` block (lines 75-92)
- **Multiple assertions:** The single test validates:
  - `tmuxSession.exists()` is true
  - `resumableAgentSessionId.agentType` is CLAUDE_CODE
  - Session ID is not blank
  - Logging of the session creation

### Helper Functions

```kotlin
resolveSystemPromptFilePath(): String          // lines 103-110
findGitRepoRoot(File): File                    // lines 112-121
```

These utilities walk up from `user.dir` to find git repo root, then resolve `config/prompts/test-agent-system-prompt.txt`.

---

## 2. Current State of Initializer.kt (AppDependencies and initialize)

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt`

### AppDependencies Data Class (lines 24-40)

```kotlin
class AppDependencies(
    val outFactory: OutFactory,
    val tmuxCommandRunner: TmuxCommandRunner,
    val tmuxCommunicator: TmuxCommunicator,
    val tmuxSessionManager: TmuxSessionManager,
    val glmDirectLLM: DirectLLM,
    private val httpClient: OkHttpClient,
) : AsgardCloseable {

    override suspend fun close() {
        // Shut down OkHttpClient connection and thread pools to prevent resource leaks
        // in long-running server usage. Order matters: dispatcher first, then connections.
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        outFactory.close()
    }
}
```

**Key Points:**
- Implements `AsgardCloseable` (suspend fun close())
- Holds all application-level dependencies
- Must be cleaned up via `.use{}` pattern

### Initializer Interface (lines 49-55)

```kotlin
interface Initializer {
    suspend fun initialize(environment: Environment = Environment.production()): AppDependencies

    companion object{
        fun standard(): Initializer = InitializerImpl()
    }
}
```

**Current Design:**
- Single public method: `initialize(environment)`
- Only parameter is Environment (no OutFactory parameter)
- Companion object provides factory method

### InitializerImpl.initialize() (lines 59-64)

```kotlin
override suspend fun initialize(environment: Environment): AppDependencies {
    val outFactory = SimpleConsoleOutFactory.standard()
    val out = outFactory.getOutForClass(InitializerImpl::class)

    return out.time({initializeImpl(outFactory, environment)}, "initializer.initialize")
}
```

**Current Behavior:**
- **Creates** SimpleConsoleOutFactory internally (line 60) — **THIS NEEDS CHANGE**
- Uses `out.time()` to time the initialization
- Delegates to private `initializeImpl()`

### InitializerImpl.initializeImpl() (lines 66-86)

**Creates:**
- `TmuxCommandRunner`
- `TmuxCommunicatorImpl`
- `TmuxSessionManager`
- `OkHttpClient` with 60-second read timeout (line 107-108)
- `GLMHighestTierApi` (via `createGLMDirectLLM()`)

**Note:** Line 67 has a TODO anchor point: `ap.ifrXkqXjkvAajrA4QCy7V.E` indicating that `environment.isTest` should be used to swap external services for test doubles (not yet implemented).

### Environment Interface (referenced)

**Location:** `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt`

```kotlin
sealed interface Environment {
    val isTest: Boolean

    companion object {
        fun production(): Environment = ProductionEnvironment()
        fun test(): Environment = TestEnvironment()
    }
}

internal class ProductionEnvironment : Environment {
    override val isTest: Boolean = false
}

internal class TestEnvironment : Environment {
    override val isTest: Boolean = true
}
```

---

## 3. Integration Test Support Infrastructure

### integTestSupport.kt

**Location:** `app/src/test/kotlin/org/example/integTestSupport.kt` (694 bytes)

```kotlin
package org.example

import com.asgard.testTools.awaitility.AsgardAwaitility

/**
 * Returns true when integration tests are enabled via the Gradle property `-PrunIntegTests=true`
 * OR when running tests inside IntelliJ IDEA.
 *
 * The Gradle property is injected by `app/build.gradle.kts` so that Gradle tracks the value
 * as a task input — cache is invalidated automatically when the property changes, unlike env vars.
 *
 * When running in IntelliJ, integration tests are always enabled to provide a seamless
 * developer experience without needing to configure run properties.
 */
fun isIntegTestEnabled(): Boolean =
    System.getProperty("runIntegTests") == "true" || TestEnvUtil.isRunningInIntelliJ
```

**Key Details:**
- Used to gate entire describe blocks: `.config(isIntegTestEnabled())`
- Returns true for Gradle property OR IntelliJ environment
- Gradle property tracked as task input (cache-aware)

### AsgardDescribeSpec Base Class

**Source:** `com.asgard.testTools.describe_spec.AsgardDescribeSpec`

**Available from test classpath via:** `testImplementation(libs.asgard.test.tools)`

**Key Features:**
- Extends Kotest `DescribeSpec`
- Provides inherited `outFactory` property (no manual construction needed)
- Supports `.config(isIntegTestEnabled())` on describe blocks
- Supports `afterEach {}` blocks for cleanup

**From Ticket Specification:**
The config structure (referenced but not shown in code):

```kotlin
data class AsgardDescribeSpecConfig(
  val shouldDumpOutLinesOnTestError: Boolean = true,
  val afterTestLogLevelVerifyConfig: AfterTestLogLevelVerifyConfig = DEFAULT,
  val autoClearOutLinesAfterTest: Boolean = false,
  val overrideLogLevelProvider: LogLevelProvider? = null,
  val shouldStopOnFirstFailure: Boolean = false,
  val testOutManager: TestOutManager = TestOutManagerStatic.getInstance(),
  val enableStructuredTestReporter: Boolean = true,
)
```

### Test Dependencies (app/build.gradle.kts)

```kotlin
testImplementation(libs.asgard.test.tools)              // Provides AsgardDescribeSpec
testImplementation(libs.kotest.assertions.core)         // Assertions (map, shouldBe, etc.)
testImplementation(libs.kotest.runner.junit5)           // JUnit 5 runner
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

---

## 4. Test Directory Structure

### Current Organization

```
app/src/test/kotlin/
├── com/glassthought/chainsaw/core/
│   ├── agent/
│   │   ├── impl/
│   │   │   └── ClaudeCodeAgentStarterBundleFactoryTest.kt
│   │   ├── starter/
│   │   │   └── impl/
│   │   │       └── ClaudeCodeAgentStarterTest.kt
│   │   └── DefaultAgentTypeChooserTest.kt
│   ├── filestructure/
│   │   └── AiOutputStructureTest.kt
│   ├── git/
│   │   ├── BranchNameBuilderTest.kt
│   │   └── GitBranchManagerIntegTest.kt
│   ├── initializer/
│   │   ├── data/
│   │   │   └── EnvironmentTest.kt
│   ├── rolecatalog/
│   │   └── RoleCatalogLoaderTest.kt
│   ├── server/
│   │   ├── PortFileManagerTest.kt
│   │   └── KtorHarnessServerTest.kt
│   ├── ticket/
│   │   ├── TicketParserTest.kt
│   │   └── YamlFrontmatterParserTest.kt
│   ├── wingman/
│   │   └── impl/
│   │       └── ClaudeCodeWingmanTest.kt
│   └── workflow/
│       └── WorkflowParserTest.kt
├── com/glassthought/directLLMApi/glm/
│   ├── GLMHighestTierApiTest.kt
│   └── GLMHighestTierApiIntegTest.kt
├── com/glassthought/initializer/
│   └── AppDependenciesCloseTest.kt
└── org/example/
    ├── AppTest.kt
    ├── InteractiveProcessRunnerTest.kt
    ├── SpawnTmuxAgentSessionUseCaseIntegTest.kt
    ├── TmuxCommunicatorIntegTest.kt
    ├── TmuxSessionManagerIntegTest.kt
    └── integTestSupport.kt
```

**Key Observations:**
- Most unit tests in their respective packages under `com/glassthought/chainsaw/core/`
- Integration tests in `org/example/` package (TmuxCommunicatorIntegTest, TmuxSessionManagerIntegTest, SpawnTmuxAgentSessionUseCaseIntegTest)
- AppDependenciesCloseTest in `com/glassthought/initializer/` — closest location to Initializer
- No existing SharedAppDep* files

### Recommended Location for SharedAppDep Classes

**Option A (Recommended):** `app/src/test/kotlin/com/glassthought/chainsaw/core/initializer/`
- Collocated with AppDependencies and Initializer
- Matches package hierarchy of production code
- Test classes should be near the code they test/support

**Option B:** `app/src/test/kotlin/org/example/`
- Collocated with current integration test infrastructure (integTestSupport.kt)
- Closer to shared test utilities

---

## 5. Auto_load Memory Files Structure

**Location:** `ai_input/memory/auto_load/`

Files follow numbered naming pattern:

```
0_env-requirement.md       (Environment prerequisites, self-healing scripts)
1_core_description.md      (Project overview, architecture decisions)
2_claude_editing.md        (How to keep CLAUDE.md updated)
3_kotlin_standards.md      (Code style, DI, logging, exception handling)
4_testing_standards.md     (BDD with DescribeSpec, one assert per test, integ test gating)
5_ticket_and_change_log_usage.md  (How to use ticket and change_log tools)
z_deep_memory_pointers.md  (Index to deep memory files)
```

### Memory File Format

Each file contains:
- **YAML Frontmatter** (optional): `desc:` field with brief description
- **Content:** Markdown with code examples, guidelines, patterns
- **References:** Cross-references via `ref.ap.XXX.E` anchor point notation
- **Wiki links:** Preserved as-is (e.g., `[[wiki.link]]`)

### Deep Memory Files

**Location:** `ai_input/memory/deep/`

```
dont_log_and_throw.md                  (Logging anti-pattern)
favor_functional_style.md              (Map/filter over loops)
in_tests__fail_hard_never_mask.md      (No silent test skipping)
in_tests__one_assert_per_test.md       (One assertion per it block)
out_logging_patterns.md                (Structured logging with Out/Val/ValType)
```

---

## 6. Existing Integration Test Patterns

### TmuxCommunicatorIntegTest.kt

**Pattern Elements:**
```kotlin
@OptIn(ExperimentalKotest::class)
class TmuxCommunicatorIntegTest : AsgardDescribeSpec({

    describe("GIVEN a tmux session running bash").config(isIntegTestEnabled()) {
        val commandRunner = TmuxCommandRunner()
        val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
        val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)
        val createdSessions = mutableListOf<TmuxSession>()
        val createdFiles = mutableListOf<File>()

        afterEach {
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed.
                }
            }
            createdSessions.clear()
            createdFiles.forEach { file -> file.delete() }
            createdFiles.clear()
        }

        describe("WHEN sendKeys with echo command") {
            it("THEN file is created with expected content") {
                // single assertion with test setup
            }
        }
    }
})
```

**Key Patterns:**
- `@OptIn(ExperimentalKotest::class)` annotation
- `.config(isIntegTestEnabled())` to gate describe block
- Mutable lists to track created resources
- `afterEach` for cleanup (robust exception handling)
- One `it` block per logical assertion
- Uses `AsgardAwaitility.wait()` for async synchronization (not `delay()`)

### TmuxSessionManagerIntegTest.kt

**Similar structure:**
- Session cleanup in `afterEach`
- Timestamp-based unique session names
- Tests both positive (exists returns true) and negative (exists returns false) cases

### AppDependenciesCloseTest.kt

**Helper Function Pattern:**
```kotlin
fun buildDepsWithHttpClient(httpClient: OkHttpClient): AppDependencies {
    val commandRunner = TmuxCommandRunner()
    val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
    return AppDependencies(
        outFactory = outFactory,
        tmuxCommandRunner = commandRunner,
        tmuxCommunicator = communicator,
        tmuxSessionManager = TmuxSessionManager(outFactory, commandRunner, communicator),
        glmDirectLLM = GLMHighestTierApi(...),
        httpClient = httpClient,
    )
}
```

**Pattern:** Helper functions inside the test class to construct objects with test-specific configurations.

---

## 7. AsgardDescribeSpec Usage Patterns Across Tests

### Standard Pattern for Unit Tests

```kotlin
class MyTest : AsgardDescribeSpec({
    describe("GIVEN some precondition") {
        describe("WHEN some action") {
            it("THEN expected outcome") {
                // assertion
            }
        }
    }
})
```

**Key Points:**
- Extends `AsgardDescribeSpec` (not `DescribeSpec`)
- No `@OptIn` annotation for unit tests
- No `.config(isIntegTestEnabled())` for unit tests
- `outFactory` is inherited from AsgardDescribeSpec

### Integration Test Pattern

```kotlin
@OptIn(ExperimentalKotest::class)
class MyIntegTest : AsgardDescribeSpec({
    describe("GIVEN...").config(isIntegTestEnabled()) {
        val resourceToClean = mutableListOf<...>()

        afterEach {
            resourceToClean.forEach { /* cleanup */ }
            resourceToClean.clear()
        }

        describe("WHEN...") {
            it("THEN...") {
                // test
            }
        }
    }
})
```

### Factory Instantiation Pattern (from AppDependenciesCloseTest)

```kotlin
fun buildTestDeps(): AppDependencies {
    val commandRunner = TmuxCommandRunner()
    val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
    // ... more setup
    return AppDependencies(...)
}
```

**Used by:** Multiple tests in AppDependenciesCloseTest to avoid duplication.

---

## 8. Available Directories for New Files

### Source Code (Production)

Primary location for SharedAppDepIntegFactory:
```
/app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/
├── Initializer.kt                    (existing)
├── data/
│   └── Environment.kt                (existing)
└── [SharedAppDepIntegFactory.kt]     (NEW)
```

Alternative location (if keeping test infrastructure separate):
```
/app/src/test/kotlin/com/glassthought/chainsaw/core/initializer/
└── [SharedAppDepIntegFactory.kt]     (test-specific variant)
```

### Test Code

Primary location for SharedAppDepDescribeSpec:
```
/app/src/test/kotlin/com/glassthought/chainsaw/core/initializer/
├── SharedAppDepIntegFactory.kt       (if placed in test/)
├── SharedAppDepDescribeSpec.kt       (NEW)
├── data/
│   └── EnvironmentTest.kt            (existing)
└── [SharedAppDepSpecConfig.kt]       (NEW, optional)
```

Alternative (collocated with other integ test utilities):
```
/app/src/test/kotlin/org/example/
├── integTestSupport.kt               (existing)
├── SharedAppDepIntegFactory.kt       (NEW)
└── SharedAppDepDescribeSpec.kt       (NEW)
```

### Memory/Documentation

For auto_load update:
```
/ai_input/memory/auto_load/
├── 4_testing_standards.md            (existing - to be updated)
├── [6_shared_app_dep_integ_testing.md] (NEW, optional dedicated memory file)
└── z_deep_memory_pointers.md         (existing - update index)
```

---

## 9. Key Changes Required to Initializer.kt

### Change 1: Add OutFactory Parameter

**Current:**
```kotlin
interface Initializer {
    suspend fun initialize(environment: Environment = Environment.production()): AppDependencies
}
```

**Target:**
```kotlin
interface Initializer {
    suspend fun initialize(
        outFactory: OutFactory,
        environment: Environment = Environment.production()
    ): AppDependencies
}
```

### Change 2: Use Provided OutFactory

**Current (line 60):**
```kotlin
override suspend fun initialize(environment: Environment): AppDependencies {
    val outFactory = SimpleConsoleOutFactory.standard()
    val out = outFactory.getOutForClass(InitializerImpl::class)

    return out.time({initializeImpl(outFactory, environment)}, "initializer.initialize")
}
```

**Target:**
```kotlin
override suspend fun initialize(outFactory: OutFactory, environment: Environment): AppDependencies {
    val out = outFactory.getOutForClass(InitializerImpl::class)

    return out.time({initializeImpl(outFactory, environment)}, "initializer.initialize")
}
```

### Change 3: Update All Callers

Must update `AppMain.kt` (or wherever initializer is called) to provide OutFactory:

```kotlin
// Example (assuming production code calls it):
val initializer = Initializer.standard()
initializer.initialize(SimpleConsoleOutFactory.standard())

// In tests, will use TestOutManager's outFactory:
val testOutManager = TestOutManager.standard()
initializer.initialize(testOutManager.outFactory)
```

---

## 10. Expected Contents of SharedAppDepIntegFactory

### Factory Responsibilities

1. **Single Instance of Initializer**
   - Created once, shared across all integration tests
   - Uses test Environment and TestOutManager

2. **Single Instance of AppDependencies**
   - Created once from Initializer
   - Held and cleaned up properly

3. **Static Methods**
   - `fun getAppDependencies(): AppDependencies`
   - `fun getInitializer(): Initializer`
   - `fun getTestOutManager(): TestOutManager`
   - `fun getDescribeSpecConfig(): AsgardDescribeSpecConfig` (returns config with test outManager)

4. **Initialization on First Use**
   - Lazy initialization pattern or eager initialization at test suite startup
   - Proper handling of suspension (runBlocking or Kotlin.test utilities)

### Expected Structure

```kotlin
object SharedAppDepIntegFactory {
    private val testOutManager: TestOutManager = TestOutManager.standard()
    private val initializer: Initializer = Initializer.standard()
    private lateinit var appDependencies: AppDependencies
    
    fun getAppDependencies(): AppDependencies {
        // Initialize on first call or return cached instance
    }
    
    fun getDescribeSpecConfig(): AsgardDescribeSpecConfig {
        return AsgardDescribeSpecConfig(
            testOutManager = testOutManager,
            // ... other config from factory
        )
    }
    
    // Cleanup hook for test suite shutdown
    suspend fun cleanup() {
        appDependencies.close()
    }
}
```

---

## 11. Expected Contents of SharedAppDepDescribeSpec

### Design Goals

1. **Reduce Boilerplate**
   - Extends AsgardDescribeSpec with test-ready config
   - Provides access to shared AppDependencies
   - No need for tests to construct their own dependencies

2. **Enforce Consistency**
   - All integration tests use the same OutFactory (TestOutManager)
   - All integration tests use the same AppDependencies
   - All integration tests have consistent logging configuration

3. **Self-Configuring**
   - No configuration required by test classes
   - Reasonable defaults from SharedAppDepIntegFactory
   - Override point if needed (but discouraged)

### Expected Structure

```kotlin
abstract class SharedAppDepDescribeSpec(
    body: DescribeSpecConfig.() -> Unit
) : AsgardDescribeSpec(
    {
        body(SharedAppDepSpecConfig())
    }
) {
    
    protected val appDependencies: AppDependencies 
        get() = SharedAppDepIntegFactory.getAppDependencies()
    
    protected val tmuxSessionManager: TmuxSessionManager
        get() = appDependencies.tmuxSessionManager
    
    protected val outFactory: OutFactory
        get() = appDependencies.outFactory
    
    // Additional shortcuts as needed by tests
    protected val tmuxCommandRunner: TmuxCommandRunner
        get() = appDependencies.tmuxCommandRunner
    
    protected val glmDirectLLM: DirectLLM
        get() = appDependencies.glmDirectLLM
}
```

**Note:** Configuration is applied at construction time via wrapper config class.

---

## 12. Testing Standards to Maintain

### From CLAUDE.md - Testing Standards (4_testing_standards.md)

1. **BDD Structure:**
   - Use `describe` blocks for GIVEN/AND structure
   - Use `it` blocks for THEN assertions
   - Nested describe blocks for complex scenarios

2. **One Assert Per Test:**
   - Each `it` block contains **one logical assertion**
   - Use describe nesting to provide context
   - No inline WHAT comments (structure provides documentation)

3. **Integration Test Gating:**
   - Entire describe blocks gated with `.config(isIntegTestEnabled())`
   - Never individual `it` blocks
   - Annotate class with `@OptIn(ExperimentalKotest::class)`

4. **Suspension Context:**
   - `describe` blocks are NOT suspend contexts
   - Suspend calls in `it` blocks or `afterEach`

5. **Resource Cleanup:**
   - Use `afterEach` for cleanup
   - Robust exception handling in cleanup (don't throw)
   - Clear mutable collections after cleanup

6. **Fail Hard, Never Mask:**
   - No silent fallbacks
   - No conditional skipping of individual tests
   - Only entire test classes/describe blocks may be gated

---

## 13. Kotlin Standards to Follow

### From CLAUDE.md - Kotlin Standards (3_kotlin_standards.md)

1. **Constructor Injection Only**
   - No DI framework, no singletons
   - Single instances wired at top-level entry point

2. **Logging (Out/Val/ValType)**
   - Never use `println` for logging
   - Use `Val(value, ValType.SPECIFIC_TYPE)` for structured values
   - Use lazy lambda for DEBUG/TRACE
   - Use snake_case for log messages
   - Never embed values in strings

3. **Resource Management**
   - Use `.use{}` pattern for AsgardCloseable
   - No resource leaks

4. **Composition Over Inheritance**
   - Use interfaces liberally
   - Prefer composition

5. **Immutability**
   - Immutable data structures by default
   - Pass values as parameters

6. **Sealed Classes/Enums**
   - No `else` branches in `when`
   - Compiler enforces exhaustiveness

---

## 14. Summary of Implementation Areas

### Phase 1: Refactor Initializer.kt
- [ ] Change `initialize()` signature to accept `OutFactory` parameter
- [ ] Remove `SimpleConsoleOutFactory.standard()` creation
- [ ] Update all callers (AppMain.kt, tests)
- [ ] Add tests for new signature

### Phase 2: Create SharedAppDepIntegFactory
- [ ] Create new file in `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/`
- [ ] Implement singleton initialization with TestOutManager
- [ ] Provide static accessors for AppDependencies, Initializer, TestOutManager
- [ ] Provide getDescribeSpecConfig() method
- [ ] Add KDoc with usage examples
- [ ] Add cleanup hook for test suite

### Phase 3: Create SharedAppDepDescribeSpec
- [ ] Create new file in `app/src/test/kotlin/com/glassthought/chainsaw/core/initializer/` OR `org/example/`
- [ ] Extend AsgardDescribeSpec with SharedAppDepSpecConfig
- [ ] Provide protected accessors for appDependencies and common components
- [ ] Add comprehensive KDoc with:
     - Usage examples
     - Comparison to direct AsgardDescribeSpec usage
     - When to use vs when to use plain AsgardDescribeSpec
- [ ] Add anchor points for documentation references

### Phase 4: Update Auto_load Memory
- [ ] Update `4_testing_standards.md` to reference SharedAppDepDescribeSpec
- [ ] Consider creating new `6_shared_app_dep_integ_testing.md` with detailed examples
- [ ] Update `z_deep_memory_pointers.md` to index new memory if created

### Phase 5: Refactor Existing Integration Tests
- [ ] Update SpawnTmuxAgentSessionUseCaseIntegTest to extend SharedAppDepDescribeSpec
- [ ] Update TmuxCommunicatorIntegTest
- [ ] Update TmuxSessionManagerIntegTest
- [ ] Remove inline dependency initialization
- [ ] Verify all tests still pass

---

## Conclusion

This exploration provides the complete context needed for implementation:

1. **Current State:** Clear understanding of SpawnTmuxAgentSessionUseCaseIntegTest and its inline initialization pattern
2. **Target Architecture:** SharedAppDepIntegFactory + SharedAppDepDescribeSpec for shared dependency management
3. **Initializer Changes:** Add OutFactory parameter to decouple from SimpleConsoleOutFactory
4. **Test Infrastructure:** Existing patterns in TmuxCommunicatorIntegTest and AppDependenciesCloseTest
5. **Standards:** Clear KDocs and guidelines from auto_load memory files
6. **File Locations:** Recommended locations for new classes (preferably in `core/initializer/`)

The implementation should follow BDD patterns, one assert per test, robust resource cleanup, and provide comprehensive KDocs with anchor points for future reference.

