# CLARIFICATION — Remove thorg-submodule

## Aligned Understanding

### Core Goal (Phase 3 of Ticket)
Switch from composite build (which requires THORG_ROOT for every build) to maven local publishing, so that:
1. Regular `./gradlew :app:build` does NOT require THORG_ROOT
2. THORG_ROOT is only needed when explicitly publishing asgard libs via `publishAsgardToMavenLocal`

### What We're NOT Doing (This Ticket)
- NOT removing the git submodule (submodules/thorg-root stays as-is)
- NOT migrating ValType → ValTypeV2 (backward-compat constructor exists; separate follow-up ticket)
- NOT changing the THORG_ROOT path (still points to submodules/thorg-root while we have it as a submodule)

### The "Adjust Out usage to refer to ValTypeV2" Clarification
`Val` already has a backward-compatible constructor for `ValType`. Existing chainsaw code compiles correctly as-is.  
This migration will be tracked as a separate follow-up ticket to keep scope focused.

## Key Decisions

### Libraries to Publish
These asgard libraries are needed by chainsaw (directly or transitively):
- `com.asgard:asgardBuildConfig:1.0.0` (already has maven-publish)
- `com.asgard:asgardCoreShared:1.0.0` (transitive of asgardCore)
- `com.asgard:asgardCoreNodeJS:1.0.0` (transitive of asgardCore)
- `com.asgard:asgardCore:1.0.0` (direct dep)
- `com.asgard:asgardCoreJVM:1.0.0` (used by asgardTestTools)
- `com.asgard:asgardTestTools:1.0.0` (direct dep, test)

### Fast Check Strategy
Check if `~/.m2/repository/com/asgard/asgardCore/1.0.0/` and `~/.m2/repository/com/asgard/asgardTestTools/1.0.0/` exist.
If both present → skip publishing. Otherwise → run publish.

### Where Tasks Live
- `publishAsgardLibsToMavenLocal`: aggregate task added in `thorgKotlinMP.build.gradle.kts` (in submodule)
- `publishAsgardToMavenLocal`: Gradle task in chainsaw root `build.gradle.kts` — delegates to submodule via exec
- `checkAsgardInMavenLocal`: Gradle task in chainsaw root `build.gradle.kts` — reports presence/absence

## Changes Required

### In submodule (submodules/thorg-root/source/libraries/kotlin-mp/)
1. `build-logic/src/main/kotlin/buildlogic.kotlin-multiplatform.gradle.kts` — Add `id("maven-publish")`
2. `build-logic/src/main/kotlin/buildlogic.kotlin-jvm.gradle.kts` — Add `id("maven-publish")`
3. `thorgKotlinMP.build.gradle.kts` — Add `publishAsgardLibsToMavenLocal` aggregate task

### In chainsaw repo
4. `settings.gradle.kts` — Remove `includeBuild("submodules/thorg-root/source/libraries/kotlin-mp")` block
5. `app/build.gradle.kts` — Add `mavenLocal()` to repositories
6. `build.gradle.kts` (root) — Add `publishAsgardToMavenLocal` and `checkAsgardInMavenLocal` tasks; update IDEA module comment
7. `ai_input/memory/auto_load/0_env-requirement.md` — Update THORG_ROOT role (no longer required for builds, only for explicit publish)
8. Regenerate CLAUDE.md via `./CLAUDE.generate.sh`

## Post-Task Follow-up Tickets
- ValTypeV2 migration for chainsaw Out/Val usages
