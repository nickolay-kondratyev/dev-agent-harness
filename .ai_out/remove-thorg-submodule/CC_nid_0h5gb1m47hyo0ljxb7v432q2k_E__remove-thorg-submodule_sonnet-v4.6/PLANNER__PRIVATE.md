# Planner Private Context — Remove Composite Build

## Decision Log

### Why publishAsgardLibsToMavenLocal goes in thorgKotlinMP.build.gradle.kts (not chainsaw)
The submodule owns its own publish lifecycle. Chainsaw's `publishAsgardToMavenLocal` is a thin delegation wrapper that invokes it via `exec`. This keeps concerns separated: if submodule changes which libraries exist, chainsaw's task doesn't need to change.

### Why exec instead of includeBuild for publishing
We could add a second `includeBuild` just for the publish task, but that would bring back the THORG_ROOT requirement during Gradle configuration time (settings.gradle.kts is evaluated eagerly). Using `exec` in a `doLast {}` block means THORG_ROOT is only checked at execution time, not configuration time.

### Why the existing tasks.withType<PublishToMavenLocal> block is not a conflict
That block reads: `tasks.withType<PublishToMavenLocal> { enabled = false }`. This operates on the ROOT project's task collection — not subprojects. In Gradle, `tasks.withType<X>` on a project only affects that project's tasks. The new `publishAsgardLibsToMavenLocal` task calls `:asgardCore:publishToMavenLocal`, `:asgardTestTools:publishToMavenLocal`, etc. — those are SUBPROJECT tasks, not root project tasks. No conflict.

### KMP maven-publish auto-wiring
Adding `id("maven-publish")` to a KMP convention plugin causes Gradle to auto-configure:
- `publishJvmPublicationToMavenLocalRepository` per JVM target
- `publishJsPublicationToMavenLocalRepository` per JS target (if declared)
- `publishKotlinMultiplatformPublicationToMavenLocalRepository` (root KMP metadata)
- `publishToMavenLocal` as an aggregate of all of the above

For chainsaw (JVM consumer), the JVM + KMP metadata publications are sufficient. The JS publications can publish too without breaking anything — they're just extra artifacts in `~/.m2`.

### The JS publish concern
`asgardCoreShared` and `asgardCoreNodeJS` have JS targets. When KMP's `publishToMavenLocal` runs for these, Gradle may try to run JS-related tasks (kotlinStoreYarnLock, etc.). Note: The root `thorgKotlinMP.build.gradle.kts` already disables `kotlinStoreYarnLock` via `tasks.withType<YarnLockStoreTask> { enabled = false }`. This should be fine, but if `publishJsPublicationToMavenLocalRepository` fails for any JS-related reason, the fallback is to call only JVM publications in the aggregate task (see risk section in DETAILED_PLANNING).

### IDEA exclusions after removing composite build
The current `build.gradle.kts` excludes everything under `submodules/thorg-root/source/` except `libraries/kotlin-mp/`. After removing the composite build, IntelliJ no longer needs `kotlin-mp` accessible. However, keeping the current exclusion pattern is harmless — it still reduces indexing noise. The comment is updated to remove the outdated "composite build" reference, but the exclusion logic stays. Implementor could optionally make it exclude `kotlin-mp` too, but that's low priority and could be a follow-up.

### Fast check implementation
`checkAsgardInMavenLocal` checks `~/.m2/repository/com/asgard/asgardCore/1.0.0/` and `~/.m2/repository/com/asgard/asgardTestTools/1.0.0/` as proxies for "all asgard libs published". This matches the CLARIFICATION decision. It uses `System.getProperty("user.home")` rather than `~` because `~` is shell expansion and not available in Gradle's `file()`.

## Risks Noted for Implementor

1. **JS publish failure**: If `publishToMavenLocal` on KMP libs fails due to JS tooling, switch to `publishJvmPublicationToMavenLocal` per library. This is explicitly documented in DETAILED_PLANNING.

2. **Order matters**: Do NOT remove the `includeBuild` block before `~/.m2` is populated. The build breaks immediately and you lose easy access to the kotlin-mp Gradle wrapper.

3. **gradle.properties in submodule**: The kotlin-mp project may have `org.gradle.parallel=true` or similar settings that affect publish behavior. If parallel publish causes issues with inter-library dependencies during publishing (unlikely but possible), add `--no-parallel` to the exec commandLine.

4. **asgardBuildConfig's own THORG_ROOT**: `asgardBuildConfig.build.gradle.kts` line 1 requires THORG_ROOT. This is fine because `publishAsgardLibsToMavenLocal` runs inside the kotlin-mp submodule context where `THORG_ROOT` is always required. This is by design.

## Files Touched Summary

| File | Location | Type of Change |
|------|----------|----------------|
| `buildlogic.kotlin-multiplatform.gradle.kts` | submodule | Add `id("maven-publish")` |
| `buildlogic.kotlin-jvm.gradle.kts` | submodule | Add `id("maven-publish")` |
| `thorgKotlinMP.build.gradle.kts` | submodule | Add `publishAsgardLibsToMavenLocal` task |
| `settings.gradle.kts` | chainsaw root | Remove `includeBuild(...)` block |
| `app/build.gradle.kts` | chainsaw app | Add `mavenLocal()` to repositories |
| `build.gradle.kts` | chainsaw root | Add 2 tasks + update IDEA comment |
| `ai_input/memory/auto_load/0_env-requirement.md` | chainsaw docs | Update THORG_ROOT description |
| `CLAUDE.md` | chainsaw root | Regenerate via `./CLAUDE.generate.sh` |
