---
id: nid_33sk1ml8zcnovfw538t464gfj_E
title: "Self-healing build: auto-publish asgard libs to maven local if missing"
status: open
deps: []
links: []
created_iso: 2026-03-10T18:16:16Z
status_updated_iso: 2026-03-10T18:16:16Z
type: task
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

When `com.asgard:asgardCore:1.0.0` or `com.asgard:asgardTestTools:1.0.0` are absent from `~/.m2`, the build fails with a cryptic configuration-cache error instead of self-healing.

## Goal
Add a Gradle task (e.g. `ensureAsgardInMavenLocal`) that:
1. Checks whether the required asgard artifacts exist in `~/.m2` (fast file-existence check, no network).
2. If any are missing, runs `publishAsgardToMavenLocal` (which requires `THORG_ROOT=$PWD/submodules/thorg-root`).
3. Is wired as a `dependsOn` of `:app:compileKotlin` (and transitively `:app:test`), so the fast path adds near-zero overhead while the slow path self-heals.

## Key files
- `app/build.gradle.kts` — wire the task dependency here
- `build.gradle.kts` (root) — `publishAsgardToMavenLocal` and `checkAsgardInMavenLocal` tasks likely live here
- `submodules/thorg-root` — source of asgard libraries; `THORG_ROOT` must point here when publishing

## Acceptance criteria
- `./gradlew :app:build` succeeds from a clean `~/.m2` (no asgard artifacts) without any manual step.
- When asgard artifacts are already present, the check completes in < 1 s (file-stat only, no Gradle daemon spin-up for publishing).
- `THORG_ROOT` is set automatically within the task (pointing to `$projectDir/submodules/thorg-root`) so no env-var export is required by the developer.

## Acceptance Criteria

- Build succeeds from scratch with empty ~/.m2/repository/com/asgard/
- Re-run with artifacts present completes the check in under 1 second
- No manual export THORG_ROOT required

