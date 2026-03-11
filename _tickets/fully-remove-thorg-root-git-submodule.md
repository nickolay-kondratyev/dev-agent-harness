---
closed_iso: 2026-03-10T18:16:05Z
id: nid_69tatkl7ajoqosoz3aal46998_E
title: "Fully remove thorg-root git submodule"
status: closed
deps: []
links: []
created_iso: 2026-03-10T17:50:06Z
status_updated_iso: 2026-03-10T18:16:05Z
type: task
priority: 3
assignee: nickolaykondratyev
tags: [asgardCore]
---

Remove the thorg-root git submodule entirely from the chainsaw repo. Instead rely on $THORG_ROOT env var pointing to a standalone checkout of thorg-root.

### Current state
- submodules/thorg-root is a git submodule (checked in under .gitmodules)
- THORG_ROOT is no longer required for ./gradlew :app:build (switched to maven local in nid_0h5gb1m47hyo0ljxb7v432q2k_E)
- THORG_ROOT is still needed for ./gradlew publishAsgardToMavenLocal

### Goal
- Remove the git submodule: `git rm --cached submodules/thorg-root` + remove .gitmodules entry + delete submodules/thorg-root directory
- Update build.gradle.kts: publishAsgardToMavenLocal task should resolve kotlin-mp dir via $THORG_ROOT (not via hardcoded submodules/ path)
  - Change: `val kotlinMpDir = java.io.File(thorgRoot, "source/libraries/kotlin-mp")`
- Update IDEA module exclusions in build.gradle.kts (the idea{} block references submodules/thorg-root which will no longer exist)
- Update ai_input/memory/auto_load/0_env-requirement.md: remove "submodules/thorg-root" references, THORG_ROOT now points to a standalone checkout
- Regenerate CLAUDE.md via ./CLAUDE.generate.sh

### Acceptance
- .gitmodules does not contain thorg-root
- submodules/thorg-root directory does not exist in the repo
- ./gradlew :app:build works (no THORG_ROOT needed, asgard libs from ~/.m2)
- export THORG_ROOT=/path/to/standalone/thorg-root && ./gradlew publishAsgardToMavenLocal works

