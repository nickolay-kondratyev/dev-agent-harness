# Implementation Review: Bring asgardTestTools and Migrate Tests to Kotest AsgardDescribeSpec

## Summary

The implementation replaces JUnit 5 `@Test`-based tests with Kotest `AsgardDescribeSpec` across all 4 test files, wires `asgardTestTools` into the composite build, cleans up the version catalog, and applies proper integration-test gating for tmux-dependent tests.

All 12 tests pass. Build is clean. The migration is well-executed, with good adherence to CLAUDE.md standards. A few issues require attention, none are critical blockers but they are real and should be addressed.

**Verdict: IMPLEMENTATION_ITERATION NOT required. Ready to ship with minor fixes noted below.**

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `TmuxCommunicatorTest` writes to `/tmp/` — conflicts with CLAUDE.md

**File:** `app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt`, line 48

```kotlin
val outputFile = File("/tmp/tmux_test_out_${System.currentTimeMillis()}")
```

**Problem:** CLAUDE.md explicitly states: "Temp files: Write to `$PWD/.tmp/`, NOT `/tmp/`."

The plan justified this as "the test creates a file in `/tmp/`. That path is fine for test artifacts (not source-controlled)." But this rationale does not override the CLAUDE.md standard.

Note: The file is written by the tmux bash process (`echo hello > /tmp/...`), not directly by the Kotlin test code. However, the _path_ is chosen by the test code, so the standard still applies.

**Fix:** Change the output path to use a `.tmp/` directory under the project root or a system temp directory using the `asgardTestTools`-provided `tmpFileInOutDirectory` helper. Example:

```kotlin
val outputFile = File(System.getProperty("user.dir"), ".tmp/tmux_test_out_${System.currentTimeMillis()}")
outputFile.parentFile.mkdirs()
```

Or better, check if `asgardTestTools` exposes a JVM-accessible `tmpFileInOutDirectory` factory for this.

---

### 2. `TmuxSessionManagerTest` has two tests that verify the same behavior (pre-existing DRY violation, preserved faithfully)

**File:** `app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt`, lines 37-55

```kotlin
describe("WHEN createSession with bash") {
    it("THEN session exists") {  // creates session, checks sessionExists == true
```
```kotlin
describe("WHEN sessionExists with existing session") {
    it("THEN returns true") {  // creates session, checks sessionExists == true — IDENTICAL behavior
```

Both tests: create a session → assert `sessionExists == true`. They test the exact same thing.

**Context:** This duplication existed in the original JUnit test file (it was faithfully migrated). This is a pre-existing issue, not introduced by this PR.

**Recommendation:** Flag as a follow-up ticket. Remove the duplicate `"WHEN sessionExists with existing session"` describe block. The `"WHEN createSession with bash"` test covers that fact that the session exists after creation. A dedicated `sessionExists` test should use a different assertion angle (e.g., verifying the return type/value for a known-not-existing session, which is already covered by `"WHEN sessionExists with non-existent name"`).

---

### 3. `4_testing_standards.md` does not mention `asgardTestTools` as the source of `AsgardDescribeSpec`

**File:** `ai_input/memory/auto_load/4_testing_standards.md`

The standards say "Unit tests extend `AsgardDescribeSpec`" but do not tell a developer where `AsgardDescribeSpec` comes from or what dependency provides it. After this migration, `asgardTestTools` is now a real `testImplementation` dependency in this project. The testing standards file should note:

- `AsgardDescribeSpec` comes from `com.asgard:asgardTestTools`
- Integration tests that require external resources must be gated with `.config(isIntegTestEnabled())` and the class annotated `@OptIn(ExperimentalKotest::class)`
- The `outFactory` property is provided by `AsgardDescribeSpec` — do not construct `NoOpOutFactory` manually in tests

**Fix:** Update `ai_input/memory/auto_load/4_testing_standards.md` to include a "Dependencies" section clarifying the above, then regenerate `CLAUDE.md` via `CLAUDE.generate.sh`.

---

## Suggestions

### 4. `AppTest` now tests infrastructure, not "app has greeting" — name is stale

**File:** `app/src/test/kotlin/org/example/AppTest.kt`

The class is still named `AppTest` but it now tests `TmuxCommandRunner` instantiation. This is accurate per the deviation note (the `App` class no longer exists), but the class name is misleading.

Consider renaming to `SanityTest` or `TmuxCommandRunnerTest`. This is a minor naming issue and not blocking.

### 5. Kotest version is hardcoded inline in `app/build.gradle.kts` — not pinned in version catalog

**File:** `app/build.gradle.kts`

```kotlin
testImplementation("io.kotest:kotest-assertions-core:5.9.1")
testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
```

The version `5.9.1` is repeated twice and is not in `gradle/libs.versions.toml`. This creates a future maintenance risk (version bump requires touching two lines, and the catalog does not reflect the actual Kotest version in use).

**Fix:** Add to `gradle/libs.versions.toml`:
```toml
[versions]
kotest = "5.9.1"

[libraries]
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
```

Then in `app/build.gradle.kts`:
```kotlin
testImplementation(libs.kotest.assertions.core)
testImplementation(libs.kotest.runner.junit5)
```

This is lower priority since the version catalog was intentionally kept minimal, but it's the established pattern for all other dependencies.

---

## Documentation Updates Needed

- `ai_input/memory/auto_load/4_testing_standards.md` — add guidance on `asgardTestTools` dependency, `@OptIn(ExperimentalKotest::class)`, `isIntegTestEnabled()` gating, and that `outFactory` is inherited (see Issue #3 above).
- After updating the auto_load file, regenerate `CLAUDE.md` via `CLAUDE.generate.sh`.
