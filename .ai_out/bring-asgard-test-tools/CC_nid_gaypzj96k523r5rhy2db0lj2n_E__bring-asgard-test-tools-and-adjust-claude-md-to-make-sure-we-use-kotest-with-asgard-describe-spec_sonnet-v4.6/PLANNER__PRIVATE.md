# Planner Private Notes

## Files Reviewed

### Build files
- `settings.gradle.kts` - composite build already includes `submodules/thorg-root/source/libraries/kotlin-mp`. Three substitutions exist. Need to add fourth for `asgardTestTools`.
- `app/build.gradle.kts` - JUnit 5 via `libs.junit.jupiter.engine` + `kotlin-test`. `useJUnitPlatform()` in test task stays (Kotest runs on JUnit Platform).
- `gradle/libs.versions.toml` - has `junit-jupiter-engine` version and library entry. Both must be removed since they will no longer be referenced. No Kotest entries needed — transitive.

### asgardTestTools build
- `asgardTestTools.build.gradle.kts` - group=`com.asgard`, version=`1.0.0`. Project path: `:asgardTestTools`. This is what goes into the substitution.
- Deps: `kotest.runner.junit5` (via jvmMain), `kotest.assertions.core`, `kotest.framework.engine` — all come transitively.
- Also brings `kotlin-awaitility` (wrapper around Awaitility) as API dep.

### AsgardDescribeSpec
- Abstract class extending Kotest `DescribeSpec`.
- Constructor: `body: AsgardDescribeSpec.() -> Unit`. Tests use `class MyTest : AsgardDescribeSpec({ ... })`.
- Provides `outFactory: OutFactory` property — no manual NoOpOutFactory needed in tests.
- `@OptIn(ExperimentalKotest::class)` is needed when using `.config(isIntegTestEnabled())` on `describe` blocks (Kotest experimental API).

### AsgardAwaitility
- `AsgardAwaitility.wait().pollDelay(D).atMost(D).until { bool }` pattern.
- Blocking (wraps Java Awaitility). Fine in test `it` blocks.
- Throws `AwaitilityConditionTimeoutException` on timeout.

### isIntegTestEnabled()
- Located in `com.asgard.testTools` package, `integTest.kt` file (jvmMain source).
- Checks `ASGARD_RUN_INTEG_TESTS=true` env var.
- Used as `.config(isIntegTestEnabled())` on describe blocks.

### Existing test files summary
1. `AppTest.kt` - trivial, one assert. Trivial migration.
2. `InteractiveProcessRunnerTest.kt` - 6 tests, `NoOpOutFactory.INSTANCE`, `runBlocking`. Drop outFactory field (use `AsgardDescribeSpec.outFactory`), drop runBlocking (it blocks are suspend), group into nested describes.
3. `TmuxSessionManagerTest.kt` - 4 tests, `@AfterEach` cleanup, tmux-dependent, `runBlocking`. Needs integration gating + `afterEach` + no runBlocking.
4. `TmuxCommunicatorTest.kt` - 1 test, `@AfterEach`, `Thread.sleep` polling, tmux-dependent. Needs AsgardAwaitility + integration gating + afterEach.

## Key Gotchas

### Kotest describe block timing
Code in `describe { }` runs at spec construction time (eager). This is fine for the current tests. The runner construction and suspend calls inside describe work because describe body is also suspend in Kotest DescribeSpec. The `it` bodies are the actual test cases.

### afterEach scope
`afterEach { }` in Kotest applies to all `it` blocks within the enclosing scope. Register it inside the same `describe` block that contains the tests needing cleanup.

### No runBlocking needed in it blocks
Kotest `it { }` bodies are `suspend`. Direct calls to suspend functions (like `sessionManager.createSession(...)`) work without `runBlocking`.

### describe body is also suspend
Variable initializations like `val result = runner.runInteractive(...)` inside a `describe { }` block work because `describe` body is suspend in Kotest. This is how the pattern `describe("GIVEN...") { val result = ...; it(...) { result shouldBe ... } }` works.

### Integration test gating
Tmux tests must be gated. Without `ASGARD_RUN_INTEG_TESTS=true`, they should skip (not fail). This is critical for CI that doesn't have tmux. The InteractiveProcessRunnerTest does NOT need gating — `echo` and `false` are standard Unix commands available everywhere.

### libs.versions.toml cleanup
The `junit-jupiter-engine` version and library entries in `gradle/libs.versions.toml` must be removed after removing the reference from `build.gradle.kts`. Gradle will fail if there's an unused version reference in some configurations. Actually — Gradle does NOT fail on unused entries in toml, but it's clean to remove them anyway to avoid confusion.

## Ordering

Phases are ordered to allow compile verification between phases:
1. Build config first → compileTestKotlin to check dependency resolution
2. Then migrate tests one by one → run tests after each
3. Verify final state

## Risk Assessment

Low risk overall. The main complexity is:
- Getting the composite build substitution right (one line addition).
- Understanding Kotest describe-block initialization timing for the test migration.
- The integration test gating pattern (needs `@OptIn(ExperimentalKotest::class)`).

No risk of breaking production code — changes are test-scope only plus build config.
