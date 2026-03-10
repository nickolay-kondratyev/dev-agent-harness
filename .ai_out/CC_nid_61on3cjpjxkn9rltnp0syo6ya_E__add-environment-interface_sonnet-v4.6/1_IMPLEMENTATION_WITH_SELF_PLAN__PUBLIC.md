# Implementation: Add Environment Interface

## What was implemented

### 1. New file: `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt`

Created the `Environment` sealed interface with:
- `val isTest: Boolean` property
- `internal class ProductionEnvironment` implementation (`isTest = false`)
- `internal class TestEnvironment` implementation (`isTest = true`)
- `companion object` with `fun production(): Environment` factory method
- KDoc on the interface explaining its purpose and sealed rationale

**Iteration 1 changes (reviewer feedback):**
- Changed `interface Environment` to `sealed interface Environment` — enables exhaustive `when` branches per CLAUDE.md
- Made `ProductionEnvironment` and `TestEnvironment` `internal` — they are implementation details; only the `Environment` sealed interface is public

### 2. Modified: `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt`

- Added `import com.glassthought.chainsaw.core.initializer.data.Environment`
- Updated `Initializer` interface: `suspend fun initialize(environment: Environment = Environment.production()): AppDependencies`
- Updated `InitializerImpl.initialize()` override to accept and pass `environment` through to `initializeImpl()`
- Updated `initializeImpl()` private method signature to accept `environment: Environment`
- `AppMain.kt` requires no change — it calls `initialize()` with no arguments, which uses the default `Environment.production()`

**Iteration 1 changes (reviewer feedback):**
- Added `// TODO(ap.ifrXkqXjkvAajrA4QCy7V.E): use environment.isTest to swap external services for test doubles` inside `initializeImpl` to make intent explicit for future readers

### 3. New test file: `app/src/test/kotlin/com/glassthought/chainsaw/core/initializer/data/EnvironmentTest.kt`

BDD-style unit test using `AsgardDescribeSpec` with two `it` blocks (one assertion each):
- `THEN isTest is false` — verifies `Environment.production()` returns `isTest = false`
- `THEN isTest is true` — verifies `TestEnvironment()` has `isTest = true`

Note: `TestEnvironment` is `internal` but test is in the same Gradle module (`app`), so access is still valid.

## Test Results

`./gradlew :app:test` — **BUILD SUCCESSFUL**

`EnvironmentTest`: 2 tests, 0 skipped, 0 failures, 0 errors

All pre-existing tests also continue to pass.

## Notes

- The `environment` parameter is currently threaded through the call chain (`initialize` → `initializeImpl`) but not yet consumed by any logic inside `initializeImpl`. The TODO comment at `ap.ifrXkqXjkvAajrA4QCy7V.E` marks the future extension point.
- `AppMain.kt` calling `InitializerImpl()` directly instead of `Initializer.standard()` is a pre-existing issue — not addressed here, a follow-up ticket exists per review.
