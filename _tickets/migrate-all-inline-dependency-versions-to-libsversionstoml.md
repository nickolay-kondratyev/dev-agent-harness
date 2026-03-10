---
id: nid_3vhqc8i3jm64uf3vkf33d9ks1_E
title: "Migrate all inline dependency versions to libs.versions.toml"
status: in_progress
deps: []
links: []
created_iso: 2026-03-10T16:33:14Z
status_updated_iso: 2026-03-10T17:37:20Z
type: chore
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [build, deps, chore]
---

$Currently app/build.gradle.kts has a mix: guava and kotest use libs.versions.toml, but OkHttp (4.12.0), Jackson (2.17.2), asgardCore, coroutines, org.json, snakeyaml, and Ktor (3.1.1) use inline version strings.\n\nThe Ktor comment in build.gradle.kts explicitly justifies this: "Inline version strings, consistent with how other dependencies (OkHttp, Jackson, asgardCore) are declared." Fixing only Ktor in the catalog would worsen consistency.\n\nFix: migrate ALL inline version strings to libs.versions.toml so there is a single source of truth. This includes OkHttp, Jackson (core + kotlin module), kotlinx-coroutines, org.json, snakeyaml, and Ktor (all 4 modules).\n\nRef: CONSOLIDATED_REVIEW.md optional #9 in .ai_out/harness-http-server-review/

