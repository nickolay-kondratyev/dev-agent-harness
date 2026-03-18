# Implementation Plan: Add detekt static analysis

## Task Understanding
Add detekt Kotlin static analysis to the Gradle project, create a baseline file so the build passes, and wire it to run as part of the test workflow.

## Codebase Recon
- Multi-module Gradle project with Kotlin 2.2.20
- Version catalog at `gradle/libs.versions.toml`
- Root `build.gradle.kts` has utility tasks (no plugins currently applied)
- `app/build.gradle.kts` is the main module
- `test.sh` runs `./gradlew :app:test` after pre-build setup

## Plan

**Goal**: Add detekt static analysis that runs with test task

**Steps**:
1. Add detekt plugin version (1.23.8) to `gradle/libs.versions.toml`
2. Apply detekt plugin in root `build.gradle.kts` with baseline configuration
3. Wire detekt to run as dependency of `:app:test` task in `app/build.gradle.kts`
4. Run `./gradlew detektBaseline` to generate baseline file
5. Run `./test.sh` to verify everything works
6. Add guidance in `ai_input/memory/auto_load/3_kotlin_standards.md` about reducing baseline

**Testing**: Run `./test.sh` to verify tests pass, run `./gradlew detekt` to verify detekt works

**Files touched**:
- `gradle/libs.versions.toml`
- `build.gradle.kts` (root)
- `app/build.gradle.kts`
- `detekt-baseline.xml` (generated)
- `ai_input/memory/auto_load/3_kotlin_standards.md`

## Progress
- [x] Step 1: Add detekt version to libs.versions.toml
- [x] Step 2: Apply detekt in app/build.gradle.kts (not root - no Kotlin sources there)
- [x] Step 3: Wire detekt to test task in app/build.gradle.kts
- [x] Step 4: Generate baseline file
- [x] Step 5: Verify with test.sh
- [x] Step 6: Add guidance in ai_input

## Final State
All steps completed successfully. Detekt is now integrated into the build pipeline.
