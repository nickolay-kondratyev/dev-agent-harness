---
id: nid_wgr6tfn0labkt9py8h24ikzwq_E
title: "Add gradle idea plugin to be able to exclude most of the submodule thorg root"
status: in_progress
deps: []
links: []
created_iso: 2026-03-07T22:10:23Z
status_updated_iso: 2026-03-07T22:56:47Z
type: task
priority: 3
assignee: nickolaykondratyev
---

We want to have gradle idea plugin exclude most of the `./submodules/thorg-root` 

Some of the sources should be included see:

```kts file=[/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/settings.gradle.kts] Lines=[16-28]
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
    }
}
```

But the rest should be auto excluded by intellij to avoid using up indexing when we stand up the project.