# Implementation Plan: Bring asgardTestTools and Migrate Tests to Kotest AsgardDescribeSpec

## 1. Problem Understanding

**Goal**: Replace the JUnit 5 test infrastructure with `asgardTestTools`, which provides `AsgardDescribeSpec` (a Kotest `DescribeSpec` extension). Migrate all 4 existing test files to the GIVEN/WHEN/THEN Kotest pattern mandated by `CLAUDE.md`.

**Key Constraints**:
- The composite build already includes `submodules/thorg-root/source/libraries/kotlin-mp` — we only need to add `asgardTestTools` to the substitution list, not add a new `includeBuild`.
- `asgardTestTools` pulls in `kotest-runner-junit5` transitively, so `useJUnitPlatform()` in the test task stays — Kotest runs on JUnit Platform.
- `AsgardDescribeSpec` is an `abstract class`. Tests extend it using the constructor lambda pattern: `class MyTest : AsgardDescribeSpec({ ... })`.
- The `outFactory` property is provided by `AsgardDescribeSpec` — no manual `NoOpOutFactory` construction needed in test bodies.
- Two tests use tmux (real external process). They continue to work as integration tests but should be gated with `isIntegTestEnabled()` via `.config(enabledIf = { isIntegTestEnabled() })` since they require tmux on the system.
- `TmuxCommunicatorTest` has a `Thread.sleep` polling anti-pattern. Replace with `AsgardAwaitility` from `asgardTestTools`.

**Assumptions**:
- `THORG_ROOT` is set (required for the composite build to resolve).
- tmux is available in the dev/CI environment for the tmux-dependent tests.
- No Kotest version entries needed in `libs.versions.toml` — they come transitively through `asgardTestTools`.

---

## 2. High-Level Architecture

### Before

```
app/build.gradle.kts
  testImplementation: kotlin-test, junit-jupiter-engine
  testRuntimeOnly: junit-platform-launcher

settings.gradle.kts (substitutions)
  asgardCore, asgardCoreShared, asgardCoreNodeJS
```

### After

```
app/build.gradle.kts
  testImplementation: com.asgard:asgardTestTools:1.0.0
  (junit-platform-launcher stays: Kotest uses JUnit Platform)
  (kotlin-test and junit-jupiter-engine removed)

settings.gradle.kts (substitutions, added)
  asgardCore, asgardCoreShared, asgardCoreNodeJS, asgardTestTools
```

### Data Flow

`./gradlew :app:test`
→ Gradle resolves `com.asgard:asgardTestTools` via composite build substitution
→ `asgardTestTools` provides `kotest-runner-junit5` transitively
→ JUnit Platform discovers Kotest tests
→ Kotest runs `AsgardDescribeSpec`-based test classes

---

## 3. Implementation Phases

### Phase 1: Build Configuration

**Goal**: Wire `asgardTestTools` into the composite build and replace JUnit 5 test dependencies.

**Files Affected**:
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/settings.gradle.kts`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/build.gradle.kts`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/gradle/libs.versions.toml`

**Key Steps**:

1. In `settings.gradle.kts`, add `asgardTestTools` to the existing `dependencySubstitution` block:
   ```kotlin
   substitute(module("com.asgard:asgardTestTools")).using(project(":asgardTestTools"))
   ```
   Place it alongside the other three substitutions. No new `includeBuild` is needed.

2. In `app/build.gradle.kts`, replace the two JUnit 5 test dependencies with `asgardTestTools`:
   - Remove: `testImplementation("org.jetbrains.kotlin:kotlin-test")`
   - Remove: `testImplementation(libs.junit.jupiter.engine)`
   - Add: `testImplementation("com.asgard:asgardTestTools:1.0.0")`
   - Keep: `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` — Kotest still uses JUnit Platform
   - Keep: `tasks.named<Test>("test") { useJUnitPlatform() }` — unchanged

3. In `gradle/libs.versions.toml`, remove the `junit-jupiter-engine` version and library entries (they are no longer referenced). The `guava` and `kotlin.jvm` plugin entries stay.

**Verification**: `./gradlew :app:compileTestKotlin` compiles without errors (even before any test migration).

---

### Phase 2: Migrate AppTest

**Goal**: Convert the trivial sanity test to AsgardDescribeSpec.

**File**:
`/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/AppTest.kt`

**Current structure**: One `@Test fun appHasAGreeting()` using `assertNotNull`.

**Migrated structure**:
```
class AppTest : AsgardDescribeSpec({
  describe("GIVEN App") {
    it("THEN greeting is not null") {
      App().greeting shouldNotBe null
    }
  }
})
```

**Import changes**:
- Remove: `kotlin.test.Test`, `kotlin.test.assertNotNull`
- Add: `com.asgard.testTools.describe_spec.AsgardDescribeSpec`, `io.kotest.matchers.shouldNotBe`

---

### Phase 3: Migrate InteractiveProcessRunnerTest

**Goal**: Convert 6 tests to AsgardDescribeSpec, using Kotest matchers and nested `describe`/`it` structure.

**File**:
`/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/InteractiveProcessRunnerTest.kt`

**Current**: 6 `@Test` methods, each using `runBlocking` inside. Uses `NoOpOutFactory.INSTANCE`.

**Key migration decisions**:
- `AsgardDescribeSpec` provides `outFactory` — use it directly. Remove the `private val outFactory = NoOpOutFactory.INSTANCE` field.
- Tests using `runBlocking` inside: Kotest `it` blocks are `suspend` — call suspend functions directly without `runBlocking`.
- Group tests logically: construction test as its own `describe`, `runInteractive` tests grouped under a `describe("WHEN runInteractive is called")`, and `InteractiveProcessResult` construction tests grouped together.

**Migrated structure** (sketch):
```
class InteractiveProcessRunnerTest : AsgardDescribeSpec({
  describe("GIVEN InteractiveProcessRunner") {
    it("WHEN constructed THEN no error") { InteractiveProcessRunner(outFactory) }

    describe("WHEN runInteractive is called") {
      val runner = InteractiveProcessRunner(outFactory)

      describe("AND command is non-interactive echo") {
        val result = runner.runInteractive("echo", "hello")

        it("THEN exit code is 0") { result.exitCode shouldBe 0 }
        it("THEN interrupted is false") { result.interrupted shouldBe false }
      }

      describe("AND command exits with non-zero") {
        val result = runner.runInteractive("false")
        it("THEN exit code is 1") { result.exitCode shouldBe 1 }
      }
    }
  }

  describe("GIVEN InteractiveProcessResult") {
    describe("WHEN constructed with exitCode 42") {
      val result = InteractiveProcessResult(exitCode = 42, interrupted = false)
      it("THEN exitCode is 42") { result.exitCode shouldBe 42 }
    }
    describe("WHEN constructed with interrupted=true") {
      val result = InteractiveProcessResult(exitCode = -1, interrupted = true)
      it("THEN interrupted is true") { result.interrupted shouldBe true }
    }
  }
})
```

**Import changes**:
- Remove: `kotlin.test.*`, `kotlinx.coroutines.runBlocking`, `com.asgard.core.out.impl.NoOpOutFactory`
- Add: `com.asgard.testTools.describe_spec.AsgardDescribeSpec`, `io.kotest.matchers.shouldBe`

**Note**: `describe` blocks in Kotest are `suspend` — variable initialization (`val result = runner.runInteractive(...)`) inside `describe` blocks executes synchronously during spec construction, so shared setup works correctly.

---

### Phase 4: Migrate TmuxSessionManagerTest

**Goal**: Convert 4 tests with `@AfterEach` cleanup to AsgardDescribeSpec, replacing `@AfterEach` with Kotest's `afterEach`/`afterSpec` block.

**File**:
`/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt`

**Current**: 4 `@Test` methods. `@AfterEach` kills sessions created during tests. Tests require tmux on the system — should be treated as integration tests and guarded.

**Key migration decisions**:
- `@AfterEach` cleanup → use Kotest's `afterEach { }` block (runs after each `it` block). The `createdSessions` mutable list tracking approach can be kept.
- Guard tmux tests as integration tests: wrap in `.config(enabledIf = { isIntegTestEnabled() })`. This is consistent with how `ProcessRunnerImplUnixyIntegTest` handles integration-gating in the asgardTestTools codebase.
- `runBlocking` removed — `it` blocks and `afterEach` blocks are `suspend`.
- `outFactory` comes from `AsgardDescribeSpec` — remove the local `NoOpOutFactory.INSTANCE` field.

**Migrated structure** (sketch):
```kotlin
@OptIn(ExperimentalKotest::class)
class TmuxSessionManagerTest : AsgardDescribeSpec({
  describe("GIVEN TmuxSessionManager").config(enabledIf = { isIntegTestEnabled() }) {
    val commandRunner = TmuxCommandRunner()
    val sessionManager = TmuxSessionManager(outFactory, commandRunner)
    val createdSessions = mutableListOf<TmuxSessionName>()

    afterEach {
      createdSessions.forEach { session ->
        try { sessionManager.killSession(session) } catch (_: Exception) { }
      }
      createdSessions.clear()
    }

    describe("WHEN createSession with bash") {
      val session = sessionManager.createSession("test-session-${System.currentTimeMillis()}", "bash")
      createdSessions.add(session)
      it("THEN session exists") { sessionManager.sessionExists(session.name) shouldBe true }
    }
    // ... additional describes
  }
})
```

**Import changes**:
- Remove: `kotlin.test.*`, `org.junit.jupiter.api.AfterEach`, `kotlinx.coroutines.runBlocking`, `com.asgard.core.out.impl.NoOpOutFactory`
- Add: `com.asgard.testTools.describe_spec.AsgardDescribeSpec`, `com.asgard.testTools.isIntegTestEnabled`, `io.kotest.common.ExperimentalKotest`, `io.kotest.matchers.shouldBe`

---

### Phase 5: Migrate TmuxCommunicatorTest

**Goal**: Convert the single tmux communicator test, replacing `Thread.sleep` polling with `AsgardAwaitility`.

**File**:
`/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt`

**Current**: 1 `@Test`, `@AfterEach` cleanup, `Thread.sleep(100)` in a polling loop.

**Key migration decisions**:
- Replace `Thread.sleep` polling with `AsgardAwaitility`:
  ```kotlin
  AsgardAwaitility.wait()
    .pollDelay(100.milliseconds)
    .atMost(5.seconds)
    .until { outputFile.exists() }
  ```
- Guard as integration test (requires tmux): `.config(enabledIf = { isIntegTestEnabled() })`.
- `afterEach` replaces `@AfterEach`. Use the same session + file cleanup approach.
- `outFactory` from `AsgardDescribeSpec` — remove local field.
- Output file: the test creates a file in `/tmp/`. That path is fine for test artifacts (not source-controlled).

**Import changes**:
- Remove: `kotlin.test.*`, `org.junit.jupiter.api.AfterEach`, `kotlinx.coroutines.runBlocking`, `com.asgard.core.out.impl.NoOpOutFactory`
- Add: `com.asgard.testTools.describe_spec.AsgardDescribeSpec`, `com.asgard.testTools.awaitility.AsgardAwaitility`, `com.asgard.testTools.isIntegTestEnabled`, `io.kotest.common.ExperimentalKotest`, `io.kotest.matchers.shouldBe`, `kotlin.time.Duration.Companion.milliseconds`, `kotlin.time.Duration.Companion.seconds`

---

### Phase 6: Verify & Clean Up

**Goal**: Confirm everything compiles and all tests pass.

**Steps**:

1. Run: `export THORG_ROOT=$PWD/submodules/thorg-root && ./gradlew :app:test`
2. Verify non-tmux tests pass unconditionally (AppTest, InteractiveProcessRunnerTest).
3. Verify tmux tests are skipped without `ASGARD_RUN_INTEG_TESTS=true` (Kotest prints a skip message).
4. Optionally run: `export ASGARD_RUN_INTEG_TESTS=true && ./gradlew :app:test` to verify tmux tests pass.

---

## 4. Technical Considerations

### Kotest `describe` Block Initialization Timing

In Kotest `DescribeSpec`, code inside `describe { }` blocks runs during **spec construction** (not lazily per test). This means:

- `val runner = InteractiveProcessRunner(outFactory)` inside a `describe` block is executed once when the spec is built.
- `val result = runner.runInteractive("echo", "hello")` inside a `describe` block runs during construction, not per `it` test.

This is generally fine for the existing tests since they are stateless across `it` blocks. For tests that need true per-`it` isolation, use `beforeEach { }` to re-initialize state. For the current tests, the existing pattern (run once, assert multiple things in separate `it` blocks) is the correct approach.

### Integration Test Gating Pattern

The `isIntegTestEnabled()` function from `com.asgard.testTools` checks for `ASGARD_RUN_INTEG_TESTS=true` environment variable. The pattern used in asgardTestTools itself is:

```kotlin
@OptIn(ExperimentalKotest::class)
class MyTest : AsgardDescribeSpec({
  describe("GIVEN ...").config(isIntegTestEnabled()) {
    // tests
  }
})
```

Note: `.config(isIntegTestEnabled())` is a shorthand — `isIntegTestEnabled()` returns `Boolean`, and Kotest `TestContainerConfig` can accept `enabled: Boolean` directly.

### `afterEach` vs `@AfterEach`

Kotest's `afterEach` lambda in `DescribeSpec` is `suspend` and runs after every `it` block within the scope where it is registered. Register it within the same `describe` scope as the tests that need cleanup.

### AsgardAwaitility is Blocking

`AsgardAwaitility.wait().until { }` is a blocking call (wraps Java Awaitility, which uses thread polling). It is safe to call from an `it` block — Kotest runs each `it` in a coroutine, and blocking within a coroutine is acceptable for test code (it just blocks the test's thread, not the coroutine dispatcher broadly). This is standard practice for awaitility-style waiting in tests.

---

## 5. Testing Strategy

### Non-integration tests (always run)
- `AppTest`: verifies `App.greeting` is non-null.
- `InteractiveProcessRunnerTest`: verifies construction, `runInteractive` with `echo` (no TTY needed), and `InteractiveProcessResult` data class behavior. These do not require tmux.

### Integration tests (require `ASGARD_RUN_INTEG_TESTS=true`)
- `TmuxSessionManagerTest`: requires tmux installed. Creates and destroys real tmux sessions.
- `TmuxCommunicatorTest`: requires tmux installed. Sends keystrokes and waits for file to appear.

### Edge Cases
- Kotest `shouldBe null` vs `shouldNotBe null` — use the correct one for `AppTest` greeting.
- `TmuxCommunicatorTest` `AsgardAwaitility` timeout: 5 seconds matches the original deadline approach.
- `afterEach` must call `createdSessions.clear()` after cleanup to prevent double-kill on next test.

---

## 6. Acceptance Criteria

All criteria are verified by running:

```bash
export THORG_ROOT=$PWD/submodules/thorg-root
./gradlew :app:test
```

1. Build succeeds (no unresolved references to JUnit 5, no missing Kotest symbols).
2. `AppTest` passes.
3. `InteractiveProcessRunnerTest` passes (all 6 tests run).
4. `TmuxSessionManagerTest` and `TmuxCommunicatorTest` are skipped (not failed) when `ASGARD_RUN_INTEG_TESTS` is unset.
5. No `@Test`, `@AfterEach`, `junit.jupiter`, `kotlin.test.Test` imports remain in test files.
6. No `Thread.sleep` remains in test files.
7. No `runBlocking` remains in test files (Kotest `it` blocks are `suspend`).
8. No `NoOpOutFactory.INSTANCE` in test files (replaced by `AsgardDescribeSpec.outFactory`).

---

## 7. Open Questions / Decisions Needed

None. The path is clear from the exploration and existing patterns in `asgardTestTools` itself. All design decisions are resolved by the existing codebase conventions.
