---
closed_iso: 2026-03-10T17:45:33Z
id: nid_3vhqc8i3jm64uf3vkf33d9ks1_E
title: "Migrate all inline dependency versions to libs.versions.toml"
status: closed
deps: []
links: []
created_iso: 2026-03-10T16:33:14Z
status_updated_iso: 2026-03-10T17:45:33Z
type: chore
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [build, deps, chore]
---

$Currently app/build.gradle.kts has a mix: guava and kotest use libs.versions.toml, but OkHttp (4.12.0), Jackson (2.17.2), asgardCore, coroutines, org.json, snakeyaml, and Ktor (3.1.1) use inline version strings.\n\nThe Ktor comment in build.gradle.kts explicitly justifies this: "Inline version strings, consistent with how other dependencies (OkHttp, Jackson, asgardCore) are declared." Fixing only Ktor in the catalog would worsen consistency.\n\nFix: migrate ALL inline version strings to libs.versions.toml so there is a single source of truth. This includes OkHttp, Jackson (core + kotlin module), kotlinx-coroutines, org.json, snakeyaml, and Ktor (all 4 modules).\n\nRef: CONSOLIDATED_REVIEW.md optional #9 in .ai_out/harness-http-server-review/


## Notes

**2026-03-10T17:45:44Z**

## Resolution

Successfully migrated all 13 inline dependency declarations to use Gradle Version Catalog.

### Changes Made
- Added 7 version entries to libs.versions.toml: asgard (1.0.0), coroutines (1.10.2), okhttp (4.12.0), json (20240303), snakeyaml (2.2), jackson (2.17.2), ktor (3.1.1)
- Added 13 library entries with dot-notation naming (e.g., libs.ktor.server.core, libs.jackson.databind)
- Updated app/build.gradle.kts to use catalog references
- Removed obsolete comment about inline version strings
- Applied version consolidation for related dependencies (asgard, okhttp, jackson, ktor)

### Verification
- Build passes: ./gradlew :app:build
- Sanity check passes: ./sanity_check.sh
- Dependencies verified via ./gradlew :app:dependencies

### Files Modified
- gradle/libs.versions.toml
- app/build.gradle.kts

Commit: c0acb85
