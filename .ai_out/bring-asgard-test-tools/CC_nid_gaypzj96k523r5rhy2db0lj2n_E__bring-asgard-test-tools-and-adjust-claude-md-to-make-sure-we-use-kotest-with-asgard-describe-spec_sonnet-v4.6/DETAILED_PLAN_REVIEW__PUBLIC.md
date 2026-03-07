# Plan Review: Bring asgardTestTools and Migrate Tests to Kotest AsgardDescribeSpec

## Executive Summary

The plan is well-scoped, technically accurate, and PARETO-aligned. It correctly identifies all
necessary build changes, and the per-file migration sketches accurately reflect the existing codebase
patterns. Two minor inconsistencies exist: an internal contradiction in the `.config()` call pattern
for integration test gating, and an incomplete description of a `TmuxCommunicatorTest` state
initialization hazard. Neither is a blocker. The plan is ready for implementation with the minor
corrections noted below.

---

## Critical Issues (BLOCKERS)

None.

---

## Major Concerns

None.

---

## Simplification Opportunities (PARETO)

**TmuxCommunicatorTest output file path**: The plan notes that `/tmp/` is "fine for test artifacts
(not source-controlled)." This is acceptable. No change needed.

**InteractiveProcessRunnerTest `describe`-level suspension**: The plan notes that `describe` blocks
run during spec construction and acknowledges this is "generally fine for the existing tests since
they are stateless." This is correct. No change needed.

---

## Minor Suggestions

### 1. Inconsistency in `.config()` call pattern for integration test gating

The plan contains an internal contradiction:

In **Section 4 (Technical Considerations)**, the plan describes:
```kotlin
describe("GIVEN ...").config(isIntegTestEnabled()) {
```
And says this is "a shorthand — `isIntegTestEnabled()` returns `Boolean`."

But in the **Phase 4 sketch code**, the plan uses the lambda form:
```kotlin
describe("GIVEN TmuxSessionManager").config(enabledIf = { isIntegTestEnabled() }) {
```

Looking at `asgardTestTools` itself (`AsgardDescribeSpecInActionIntegTest.kt` and
`ProcessRunnerImplUnixyIntegTest.kt`), the Boolean shorthand `.config(isIntegTestEnabled())` is the
prevalent pattern. The `enabledIf = { ... }` lambda form also exists but appears primarily in the
`kotlin-jvm` (non-KMP) modules.

**Recommendation**: Use `.config(isIntegTestEnabled())` consistently — it is the simpler, more
idiomatic form within the KMP module context and matches how `asgardTestTools` tests gate themselves.
Update Phase 4 and Phase 5 sketch code to use this form. The `@OptIn(ExperimentalKotest::class)`
annotation is still required.

### 2. Phase 3 `InteractiveProcessRunnerTest` — `outFactory` access from `describe` lambda

The plan says `outFactory` from `AsgardDescribeSpec` can be used directly inside `describe` blocks.
This is correct per the actual `AsgardDescribeSpec` source (the `outFactory` property is defined on
the class itself and is accessible in the constructor lambda body). The plan sketch correctly uses
`outFactory` without qualification. No change needed, but implementor should note: `outFactory` is
accessible because the constructor lambda receives `AsgardDescribeSpec.()` as its receiver.

### 3. Phase 4 `TmuxSessionManagerTest` — mutable state in `describe` scope

The plan uses:
```kotlin
val createdSessions = mutableListOf<TmuxSessionName>()
```
...declared inside a `describe` block alongside `afterEach`. Since Kotest `DescribeSpec` initializes
all `describe` blocks once during spec construction and runs `it` blocks multiple times, this
`mutableListOf` is shared across all `it` executions in that scope. The plan already correctly
handles cleanup via `afterEach` + `createdSessions.clear()`. Implementor should ensure `clear()` is
called in `afterEach` (the plan says so — just flagging to verify in implementation).

### 4. Phase 6 verification — redirect build output

Per CLAUDE.md conventions for verbose build output: the test verification command should redirect to
a `.tmp/` file rather than flooding the terminal. The plan currently shows:

```bash
export THORG_ROOT=$PWD/submodules/thorg-root && ./gradlew :app:test
```

**Recommendation**: Document as:
```bash
mkdir -p .tmp/
export THORG_ROOT=$PWD/submodules/thorg-root && ./gradlew :app:test > .tmp/test-run.txt 2>&1
```

### 5. `libs.versions.toml` cleanup — confirm no other references before removing

The plan says to remove the `junit-jupiter-engine` version and library entries from
`gradle/libs.versions.toml`. The file currently has exactly two entries beyond `guava`:
- `[versions]` entry `junit-jupiter-engine = "5.12.1"`
- `[libraries]` entry `junit-jupiter-engine = ...`

These are only referenced in `app/build.gradle.kts` via `libs.junit.jupiter.engine`. After removing
that reference in Phase 1, the toml cleanup is safe. No issues. Just confirming the plan is correct
here.

---

## Strengths

- **Build wiring is correct**: Adding `asgardTestTools` to the existing `dependencySubstitution`
  block (not a new `includeBuild`) is exactly right. The exploration correctly identified that the
  composite build already includes the full `kotlin-mp` tree.

- **Transitive dependency awareness**: The plan correctly identifies that `asgardTestTools` brings
  `kotest-runner-junit5` transitively, so `useJUnitPlatform()` stays and no explicit Kotest version
  entries are needed in `libs.versions.toml`.

- **`runBlocking` removal is justified**: Kotest `it` blocks are `suspend`, so the removal of
  `runBlocking` wrappers is both correct and required. The plan explains this clearly.

- **`afterEach` vs `@AfterEach`**: The migration to Kotest's `afterEach` is correctly scoped and
  the plan correctly places it within the `describe` block it services.

- **`AsgardAwaitility` usage**: The proposed replacement of the `Thread.sleep` polling loop in
  `TmuxCommunicatorTest` with `AsgardAwaitility.wait().pollDelay(100.milliseconds).atMost(5.seconds).until { }` is correct. The API matches the actual `AsgardConditionFactory` implementation.

- **Integration test gating is right**: Tmux tests are correctly identified as integration tests
  needing `isIntegTestEnabled()` gating. The non-tmux tests (`AppTest`, `InteractiveProcessRunnerTest`) correctly run unconditionally.

- **One assert per `it` block**: The migration sketches follow the one-assert-per-`it` rule
  mandated by CLAUDE.md. The `InteractiveProcessRunnerTest` sketch correctly separates the exit
  code and `interrupted` checks into distinct `it` blocks rather than combining them.

- **`NoOpOutFactory.INSTANCE` removal**: The plan correctly identifies that `outFactory` is provided
  by `AsgardDescribeSpec` and removes all manual `NoOpOutFactory.INSTANCE` fields, which is cleaner
  and more aligned with how the base class is meant to be used.

- **PARETO-aligned**: The plan delivers the full migration without over-engineering. It does not
  introduce unnecessary abstractions, does not restructure the test hierarchy beyond what the BDD
  pattern requires, and does not add any scope beyond the task.

---

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**Plan is ready for implementation.** The two items to fix during implementation are:

1. Use `.config(isIntegTestEnabled())` (Boolean shorthand) consistently in both Phase 4 and Phase 5
   sketch code, matching the pattern used in `asgardTestTools` itself. The `enabledIf = { ... }`
   lambda form in the Phase 4 sketch should be updated.

2. Redirect `./gradlew :app:test` output to `.tmp/` per CLAUDE.md bash conventions during Phase 6
   verification.

Neither requires plan iteration — these can be applied directly during implementation.

**No PLAN_ITERATION required.**
