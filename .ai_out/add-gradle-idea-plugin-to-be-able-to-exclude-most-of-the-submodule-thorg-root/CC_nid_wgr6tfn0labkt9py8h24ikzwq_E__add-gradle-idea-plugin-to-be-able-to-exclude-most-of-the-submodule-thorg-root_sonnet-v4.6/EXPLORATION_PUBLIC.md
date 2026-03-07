# EXPLORATION_PUBLIC.md

## Task Summary
Add Gradle IDEA plugin to exclude most of `./submodules/thorg-root` from IntelliJ indexing.
Keep `submodules/thorg-root/source/libraries/kotlin-mp` accessible (it's a composite build).

## Project Structure
- Root: `nickolay-kondratyev_dev-agent-harness` (single-project + composite build)
- `settings.gradle.kts`: root settings, includes composite build from `submodules/thorg-root/source/libraries/kotlin-mp`
- `app/build.gradle.kts`: application module (Kotlin JVM + application plugin)
- **No root `build.gradle.kts` exists** — needs to be created

## Composite Build Configuration (settings.gradle.kts)
```kts
includeBuild("submodules/thorg-root/source/libraries/kotlin-mp") {
    dependencySubstitution {
        substitute(module("com.asgard:asgardCore")).using(project(":asgardCore"))
        substitute(module("com.asgard:asgardCoreShared")).using(project(":asgardCoreShared"))
        substitute(module("com.asgard:asgardCoreNodeJS")).using(project(":asgardCoreNodeJS"))
    }
}
```

## thorg-root submodule structure
```
submodules/thorg-root/
  source/
    app/           ← EXCLUDE
    gradle_with_config.sh  ← file, not dir
    libraries/
      kotlin-mp/   ← KEEP (composite build)
    poc/           ← EXCLUDE
    tampermonkey/  ← EXCLUDE
    tools/         ← EXCLUDE
  (62 total files/dirs in root — mix of docs, scripts, _tickets, ai_input, etc.)
```

## Implementation Approach
The Gradle IDEA plugin allows configuring IntelliJ IDEA module settings via Gradle.
When applied to the root project, it controls the root module's `excludeDirs`.

**Key decision**: Apply at root project level (not `app`) because `submodules/thorg-root`
is under the root project's content root, not the `app` module's content root.

**Plan**:
1. Create root `build.gradle.kts` with `idea` plugin
2. Configure `idea.module.excludeDirs` to exclude all of `submodules/thorg-root`
   except `source/libraries/kotlin-mp`
   - Exclude all direct children of `thorg-root/` except `source/`
   - Exclude all direct children of `thorg-root/source/` except `libraries/`
   - Exclude all direct children of `thorg-root/source/libraries/` except `kotlin-mp/`
   (kotlin-mp is the ONLY library currently, but the code should be robust)

## CLARIFICATION Decision
No ambiguities. Requirements are clear. Proceeding directly to implementation.
