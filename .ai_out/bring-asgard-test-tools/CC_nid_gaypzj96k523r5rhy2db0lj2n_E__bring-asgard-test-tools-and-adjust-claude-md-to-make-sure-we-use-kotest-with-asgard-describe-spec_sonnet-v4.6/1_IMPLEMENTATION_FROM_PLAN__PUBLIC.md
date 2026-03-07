# Implementation: Bring asgardTestTools and Migrate Tests to Kotest AsgardDescribeSpec

## Status: COMPLETE

All 12 tests pass. Build successful.

---

## Files Changed

### Build Configuration

**`settings.gradle.kts`**
- Added `substitute(module("com.asgard:asgardTestTools")).using(project(":asgardTestTools"))` to the composite build substitution block.

**`app/build.gradle.kts`**
- Removed: `testImplementation("org.jetbrains.kotlin:kotlin-test")`
- Removed: `testImplementation(libs.junit.jupiter.engine)`
- Added: `testImplementation("com.asgard:asgardTestTools:1.0.0")`
- Added: `testImplementation("io.kotest:kotest-assertions-core:5.9.1")` (see deviation note)
- Added: `testImplementation("io.kotest:kotest-runner-junit5:5.9.1")` (see deviation note)
- Kept: `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`
- Kept: `tasks.named<Test>("test") { useJUnitPlatform() }`

**`gradle/libs.versions.toml`**
- Removed: `junit-jupiter-engine` version entry
- Removed: `junit-jupiter-engine` library entry

### Test Files

**`app/src/test/kotlin/org/example/AppTest.kt`**
- Migrated from JUnit 5 `@Test` to `AsgardDescribeSpec`
- Updated to test `TmuxCommandRunner` instantiation (see deviation note)

**`app/src/test/kotlin/org/example/InteractiveProcessRunnerTest.kt`**
- Migrated from JUnit 5 to `AsgardDescribeSpec`
- Removed `NoOpOutFactory.INSTANCE` — uses `outFactory` from `AsgardDescribeSpec`
- Removed `runBlocking` — suspend calls moved inside `it` blocks (see deviation note)
- 6 `it` blocks covering construction, non-interactive echo, non-zero exit, data class fields

**`app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt`**
- Migrated from JUnit 5 to `AsgardDescribeSpec`
- `@AfterEach` → Kotest `afterEach { }` within the describe scope
- Removed `NoOpOutFactory.INSTANCE` and `runBlocking`
- Integration test gated with `.config(isIntegTestEnabled())`
- `@OptIn(ExperimentalKotest::class)` on class

**`app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt`**
- Migrated from JUnit 5 to `AsgardDescribeSpec`
- `Thread.sleep` polling replaced with `AsgardAwaitility.wait().pollDelay(100.milliseconds).atMost(5.seconds).until { outputFile.exists() }`
- `@AfterEach` → Kotest `afterEach { }`
- Removed `NoOpOutFactory.INSTANCE` and `runBlocking`
- Integration test gated with `.config(isIntegTestEnabled())`
- `@OptIn(ExperimentalKotest::class)` on class

---

## Test Run Results

**Total: 12 tests, 0 failures, 0 skipped**
- `AppTest`: 1 test pass
- `InteractiveProcessRunnerTest`: 6 tests pass
- `TmuxSessionManagerTest`: 4 tests pass (tmux available + ASGARD_RUN_INTEG_TESTS=true in env)
- `TmuxCommunicatorTest`: 1 test pass (tmux available + ASGARD_RUN_INTEG_TESTS=true in env)

---

## Deviations from Plan

### 1. Kotest dependencies declared explicitly in `app/build.gradle.kts`

**Plan said**: "asgardTestTools pulls in kotest-runner-junit5 transitively"

**Reality**: `asgardTestTools` declares Kotest with `implementation` (not `api`) in its build file. In Gradle, `implementation` dependencies are NOT exported to consumers' compilation classpaths. The `app` module therefore cannot see `io.kotest.*` classes from the transitive graph.

**Fix**: Added `io.kotest:kotest-assertions-core:5.9.1` and `io.kotest:kotest-runner-junit5:5.9.1` as direct `testImplementation` in `app/build.gradle.kts`. Using the same version (5.9.1) as declared in `asgardTestTools`'s `libs.versions.toml`.

### 2. Suspend calls cannot be in `describe` block bodies — moved to `it` blocks

**Plan said**: "`val result = runner.runInteractive(...)` inside a `describe` block executes synchronously during spec construction, so shared setup works correctly"

**Reality**: Kotest's `describe` body lambda is NOT a suspend context. Calling suspend functions (like `runInteractive`, `createSession`, `sessionExists`, `killSession`, `sendKeys`) directly inside a `describe { }` block causes compile errors: "Suspend function can only be called from a coroutine or another suspend function." Only `it { }` and `afterEach { }` blocks are suspend.

**Fix**: Moved all suspend calls inside `it` blocks. For `InteractiveProcessRunnerTest`, the echo command is re-run in each `it` block (fast, non-interactive). For `TmuxSessionManagerTest` and `TmuxCommunicatorTest`, the full setup (session creation) is done inside each `it` block with `afterEach` for cleanup. Non-suspend instantiation (`TmuxCommandRunner()`, `InteractiveProcessRunner(outFactory)`) stays in the `describe` body.

### 3. AppTest updated to test TmuxCommandRunner instead of App class

**Plan said**: test `App().greeting shouldNotBe null`

**Reality**: The `App` class does not exist in the codebase. The project uses a top-level `main()` function (not an `App` class). The original generated `App` class was removed in an earlier commit. The test was already broken before this migration.

**Fix**: Updated AppTest to test `TmuxCommandRunner()` instantiation — a meaningful sanity check for the project's primary infrastructure. The test description is updated to be transparent about what is being tested.

---

## Verification Checklist

- [x] No `@Test`, `@AfterEach`, `junit.jupiter`, `kotlin.test.Test` imports remain
- [x] No `Thread.sleep` remains
- [x] No `runBlocking` remains in test bodies
- [x] No `NoOpOutFactory.INSTANCE` in test files
- [x] All tests compile and pass
- [x] `AppTest` and `InteractiveProcessRunnerTest` run unconditionally
- [x] `TmuxSessionManagerTest` and `TmuxCommunicatorTest` gated with `isIntegTestEnabled()`
