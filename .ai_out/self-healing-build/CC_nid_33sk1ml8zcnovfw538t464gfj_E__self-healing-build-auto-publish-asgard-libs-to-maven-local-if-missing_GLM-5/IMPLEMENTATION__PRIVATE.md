# Implementation Plan: Self-Healing Build for Asgard Libraries

## Goal
Create `ensureAsgardInMavenLocal` Gradle task that auto-publishes asgard libraries if missing, eliminating manual THORG_ROOT setup.

## Steps

1. [x] Add `ensureAsgardInMavenLocal` task to root `build.gradle.kts`
   - Check if artifacts exist (fast file stat)
   - If missing, auto-publish with THORG_ROOT set automatically
   - Use `outputs.upToDateWhen` for fast-path caching

2. [x] Wire task dependency in `app/build.gradle.kts`
   - Add `dependsOn(":ensureAsgardInMavenLocal")` to `compileKotlin`
   - This transitively covers `:app:test`

3. [x] Test the implementation
   - Verify artifacts missing → auto-publish works
   - Verify artifacts present → fast path (< 1s)

## Files Touched
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/build.gradle.kts`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/build.gradle.kts`

## Current State
- [x] Read existing build files
- [x] Understand existing task structure
- [x] Implement changes
- [x] Test

## Test Results
1. **Auto-publish test**: Removed artifacts, ran `ensureAsgardInMavenLocal` → auto-published successfully
2. **Fast path test**: With artifacts present, task completes in 413ms (< 1s requirement met)
3. **Task dependency test**: `:app:compileKotlin` correctly depends on `:ensureAsgardInMavenLocal`
4. **Build workflow test**: Full `:app:compileKotlin` works end-to-end

## Notes
- The existing `publishAsgardToMavenLocal` requires THORG_ROOT env var
- New task will auto-set THORG_ROOT to `$projectDir/submodules/thorg-root`
- Using `outputs.upToDateWhen` for Gradle's incremental build optimization
