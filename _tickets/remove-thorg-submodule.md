---
closed_iso: 2026-03-10T17:16:36Z
id: nid_0h5gb1m47hyo0ljxb7v432q2k_E
title: "Remove thorg-submodule"
status: closed
deps: []
links: []
created_iso: 2026-03-10T01:07:52Z
status_updated_iso: 2026-03-10T17:16:36Z
type: task
priority: 3
assignee: nickolaykondratyev
---

GOAL: remove thorg-root submodule and pull the asgard libraries from the m2 local maven repository instead. 

### High level:
1) Have Asgard core and other Asgard dependencies build to m2 local.
2) Adjust docker to share m2 directory between docker instances. - DONE
3) Switch this repo to pull asgard core from maven including local and clean up any references to THORG_ROOT in doc. Adjust Out usage to refer to ValTypeV2


--------------------------------------------------------------------------------
### Details

Right now we include the following libraries from the kotlin-mp which forces to have the submodule of thorg root.

```kts file=[$(git.repo_root)/settings.gradle.kts] Lines=[16-28]
// Include the full kotlin-mp build from the submodule as a composite build.
// This makes com.asgard:asgardCore (and all other com.asgard/com.thorg artifacts)
// resolvable directly from source without publishing to Maven.
includeBuild("submodules/thorg-root/source/libraries/kotlin-mp") {
    dependencySubstitution {
        substitute(module("com.asgard:asgardCore")).using(project(":asgardCore"))
        // asgardCoreShared and asgardCoreNodeJS are transitive deps of asgardCore (not direct deps of this project).
        // They must be substituted here so the composite build can resolve them from source as well.
        substitute(module("com.asgard:asgardCoreShared")).using(project(":asgardCoreShared"))
        substitute(module("com.asgard:asgardCoreNodeJS")).using(project(":asgardCoreNodeJS"))
        substitute(module("com.asgard:asgardTestTools")).using(project(":asgardTestTools"))
    }
}
```

As well as having this environment variable set:
```md file=[$(git.repo_root)/ai_input/memory/auto_load/0_env-requirement.md] Lines=[2-15]
## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)

### `THORG_ROOT` (required)
The build depends on `THORG_ROOT` being set in the environment. Without it, `./gradlew :app:build` will fail.

`THORG_ROOT` must point to the root of the `thorg-root` submodule (checked in under `submodules/thorg-root`):

export THORG_ROOT=$PWD/submodules/thorg-root

This is needed because the composite build in `settings.gradle.kts` includes
`submodules/thorg-root/source/libraries/kotlin-mp`, and that build uses `THORG_ROOT` internally
(e.g., for resolving version catalogs and sub-project paths).
```

GOAL: Let's add publishing to local maven from asgard libraries in thorg-root so that they can have a gradle target (which triggerred explicitly not part of build of those asgard libs (to not slow down) that publishes the asgard libraries to local maven.

In our library we will depend on the published versions of the library.

In our chainsaw Gradle builds we will have a target that can trigger the publishing of asgard library, using $THORG_ROOT env variable as reference point to find where asgard libraries are located. ($THORG_ROOT later will not be a submodule but it will point to the same file structure as /home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/submodules/thorg-root currently does). In chainsaw builds we should have a fast way to check whether we need to trigger this publish to local maven or whether the libraries are already present in local maven cache.


## Notes

**2026-03-10T17:16:36Z**

Resolution: switched from Gradle composite build to maven local.
- Removed includeBuild(submodules/thorg-root) from settings.gradle.kts
- Added mavenLocal() to app/build.gradle.kts  
- Added publishAsgardToMavenLocal + checkAsgardInMavenLocal tasks to root build.gradle.kts
- Added id(maven-publish) to buildlogic convention plugins in submodule
- Added publishAsgardLibsToMavenLocal aggregate task in thorgKotlinMP.build.gradle.kts
- Submodule changes committed to thorg-root git history (commit 553eb324f)
- THORG_ROOT no longer required for ./gradlew :app:build
- Build and tests verified passing without THORG_ROOT set
