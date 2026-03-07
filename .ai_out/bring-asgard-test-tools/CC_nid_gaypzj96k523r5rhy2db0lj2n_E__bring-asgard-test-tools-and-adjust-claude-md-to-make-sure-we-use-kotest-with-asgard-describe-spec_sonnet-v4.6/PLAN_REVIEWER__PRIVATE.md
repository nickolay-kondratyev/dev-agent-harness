# Plan Reviewer Private Notes

## Files Examined

### Current Codebase
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/settings.gradle.kts`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/build.gradle.kts`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/gradle/libs.versions.toml`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/AppTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/InteractiveProcessRunnerTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt`

### Submodule: Reference Implementation
- `asgardTestTools/src/commonMain/kotlin/com/asgard/testTools/describe_spec/AsgardDescribeSpec.kt`
- `asgardTestTools/src/jvmMain/kotlin/com/asgard/testTools/integTest.kt`
- `asgardTestTools/src/jvmMain/kotlin/com/asgard/testTools/awaitility/AsgardAwaitility.kt`
- `asgardTestTools/src/jvmMain/kotlin/com/asgard/testTools/awaitility/AsgardConditionFactory.kt`
- `asgardTestTools/src/jvmTest/kotlin/com/asgard/testTools/AsgardDescribeSpecInActionIntegTest.kt`
- `asgardTestTools/src/jvmTest/kotlin/com/asgard/testTools/processRunner/ProcessRunnerImplUnixyIntegTest.kt`

## Key Facts Verified

### Build configuration
- `settings.gradle.kts` currently has 3 substitutions: `asgardCore`, `asgardCoreShared`, `asgardCoreNodeJS`. The plan correctly says to add `asgardTestTools` to this same block.
- `app/build.gradle.kts` has `testImplementation("org.jetbrains.kotlin:kotlin-test")` and `testImplementation(libs.junit.jupiter.engine)`. Plan correctly removes both.
- `gradle/libs.versions.toml` has `junit-jupiter-engine` version and library entries. Plan correctly removes both after the dep reference is gone.
- `junit-platform-launcher` is in `testRuntimeOnly` — plan correctly keeps this (Kotest uses JUnit Platform).

### AsgardDescribeSpec
- It is `abstract class AsgardDescribeSpec(body: AsgardDescribeSpec.() -> Unit = {}, ...)`.
- `outFactory` is a `val` on the class, accessible from the `body` lambda since the receiver is `AsgardDescribeSpec`.
- No `NoOpOutFactory` needed in tests that extend `AsgardDescribeSpec`.

### Integration test gating patterns
- `.config(isIntegTestEnabled())` — Boolean shorthand, most common in KMP modules. Used in `AsgardDescribeSpecInActionIntegTest`, `ProcessRunnerImplUnixyIntegTest`, `AddTestRanMarker`, etc.
- `.config(enabledIf = { isIntegTestEnabled() })` — Lambda form, used primarily in kotlin-jvm (Micronaut) modules.
- **Both compile**. But the Boolean shorthand is the idiomatic form for KMP modules. The plan inconsistently uses the lambda form in Phase 4 sketch while describing the Boolean form in Section 4.

### AsgardAwaitility
- `AsgardAwaitility.wait()` returns `AsgardConditionFactory`.
- `AsgardConditionFactory.pollDelay(Duration)` and `.atMost(Duration)` both accept Kotlin `Duration`.
- `.until { Boolean }` is a blocking call — safe in Kotest `it` blocks.
- The plan's proposed usage is correct.

### Kotest DescribeSpec initialization
- Code in `describe { }` blocks runs during spec construction (eager), not per `it`.
- `val result = runner.runInteractive(...)` in a `describe` block executes once. This is correct for tests where the same result is asserted in multiple `it` blocks.
- `afterEach` runs after each `it` block within its scope.

## Decision: No PLAN_ITERATION needed

The inconsistency in `.config()` call pattern is a minor fix that can be applied during implementation. The plan's core technical decisions are all correct and verified against the actual source.

## Risks not in the plan

None identified that weren't already addressed. The plan mentions "Kotest prints a skip message" when gated tests are disabled — this is correct behavior from `isIntegTestEnabled()` which prints a console-colored message when env var is not set.

The one thing worth watching: `TmuxSessionManagerTest` currently initializes `sessionManager` and `commandRunner` at the class level (not inside a describe block). In the migrated form, these will be initialized inside the `describe` block during spec construction. Since `AsgardDescribeSpec.outFactory` is available when the body lambda executes, this is fine — the `outFactory` property is initialized before `body()` is called (see `AsgardDescribeSpec.init { addExtensions(); body() }`).
