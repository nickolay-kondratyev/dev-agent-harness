# Add kotlinx-coroutines-test Dependency

## What was done

Added the `kotlinx-coroutines-test` library to the project and created a minimal proof test demonstrating virtual time control.

### Changes

1. **`gradle/libs.versions.toml`** -- Added `kotlinx-coroutines-test` library entry using the existing `kotlinx-coroutines` version ref (`1.10.2`).

2. **`app/build.gradle.kts`** -- Added `testImplementation(libs.kotlinx.coroutines.test)` to the dependencies block.

3. **`app/src/test/kotlin/com/glassthought/shepherd/coroutines/VirtualTimeProofTest.kt`** -- Created a minimal BDD-style proof test that:
   - Extends `AsgardDescribeSpec`
   - Uses `runTest` with `advanceTimeBy(1000)` to prove virtual time works
   - Asserts `testScheduler.currentTime` equals 1000 after advancement

### Test Results

All tests pass (exit code 0) including the new `VirtualTimeProofTest`.
