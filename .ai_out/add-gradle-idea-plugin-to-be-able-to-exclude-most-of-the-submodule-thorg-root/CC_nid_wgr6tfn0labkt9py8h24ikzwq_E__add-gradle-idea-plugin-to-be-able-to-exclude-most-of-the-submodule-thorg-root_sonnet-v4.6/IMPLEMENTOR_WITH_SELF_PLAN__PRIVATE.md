# IMPLEMENTOR_WITH_SELF_PLAN__PRIVATE

## Task Understanding
Add Gradle IDEA plugin to root project to exclude most of `./submodules/thorg-root` from IntelliJ IDEA indexing,
while keeping `submodules/thorg-root/source/libraries/kotlin-mp` accessible.

## Plan

**Goal**: Create a root `build.gradle.kts` that applies the `idea` plugin and configures exclusions for thorg-root.

**Steps**:
- [x] Read existing files (settings.gradle.kts, app/build.gradle.kts)
- [x] Verify root build.gradle.kts does NOT exist
- [x] Understand thorg-root directory structure
- [x] Create `build.gradle.kts` at repo root with `idea` plugin + exclusion configuration
- [x] Run build to verify it still compiles cleanly

**Exclusion logic**:
- Exclude all direct children of `thorg-root/` that are NOT `source/`
- Exclude all direct children of `thorg-root/source/` that are NOT `libraries/`
- Exclude all direct children of `thorg-root/source/libraries/` that are NOT `kotlin-mp/`

**Files touched**:
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/build.gradle.kts` (create)

## Status
COMPLETE
