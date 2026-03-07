# Implementation Iteration: Review Fixes

## Status: COMPLETE

All three required fixes applied. All tests pass (BUILD SUCCESSFUL, 12 tests green).

---

## Fix 1: `/tmp/` path in TmuxCommunicatorTest

**File:** `app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt`

Changed the temp output file path from `/tmp/tmux_test_out_...` to a project-relative `.tmp/` directory, per CLAUDE.md standard ("Temp files: Write to `$PWD/.tmp/`, NOT `/tmp/`").

```kotlin
// Before
val outputFile = File("/tmp/tmux_test_out_${System.currentTimeMillis()}")

// After
val outputFile = File(System.getProperty("user.dir"), ".tmp/tmux_test_out_${System.currentTimeMillis()}")
outputFile.parentFile.mkdirs()
```

---

## Fix 2: `ai_input/memory/auto_load/4_testing_standards.md` updated

**File:** `ai_input/memory/auto_load/4_testing_standards.md`

Added a "Dependencies" section and "Integration Tests" section, and "Suspend Context" clarification. New content covers:
- `AsgardDescribeSpec` comes from `testImplementation("com.asgard:asgardTestTools:1.0.0")`
- Kotest deps must be declared explicitly (they are `implementation`, not `api`, in `asgardTestTools`)
- `outFactory` is inherited — do NOT construct `NoOpOutFactory` manually
- Integration tests: use `.config(isIntegTestEnabled())` + `@OptIn(ExperimentalKotest::class)`
- `describe` block bodies are NOT suspend — suspend calls go inside `it` or `afterEach`

CLAUDE.md was regenerated via `CLAUDE.generate.sh` after the update.

---

## Fix 3: Kotest version moved to version catalog

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`

Added `kotest = "5.9.1"` to `[versions]` and two library entries to `[libraries]` in the version catalog. Updated `app/build.gradle.kts` to use `libs.kotest.assertions.core` and `libs.kotest.runner.junit5` instead of hardcoded inline strings.

`gradle/libs.versions.toml` additions:
```toml
[versions]
kotest = "5.9.1"

[libraries]
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
```

`app/build.gradle.kts` updated:
```kotlin
testImplementation(libs.kotest.assertions.core)
testImplementation(libs.kotest.runner.junit5)
```

---

## Follow-up Ticket Created

**Ticket ID:** `nid_e6xmtbw1d539id72io3voxnxe_E`
**Title:** "Remove duplicate session-exists test in TmuxSessionManagerTest"

The duplicate behavior in `TmuxSessionManagerTest` (two tests that both create a session and assert `sessionExists == true`) is tracked for future cleanup.

---

## Final Test Results

```
BUILD SUCCESSFUL in 6s
28 actionable tasks: 3 executed, 25 up-to-date
```

All 12 tests green. Build clean.
