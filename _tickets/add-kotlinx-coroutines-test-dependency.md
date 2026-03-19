---
id: nid_c9jhmwry79x87j1h27qh4tvle_E
title: "Add kotlinx-coroutines-test dependency"
status: open
deps: []
links: []
created_iso: 2026-03-19T00:29:10Z
status_updated_iso: 2026-03-19T00:29:10Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [build, testing, dependencies]
---

## Context

Spec: `doc/core/AgentFacade.md` (ref.ap.9h0KS4EOK5yumssRCJdbq.E), R5.

## What to Implement

1. Add `kotlinx-coroutines-test` to `gradle/libs.versions.toml`
2. Add `testImplementation(libs.kotlinx.coroutines.test)` to `app/build.gradle.kts`
3. Verify with a minimal `runTest {}` + `advanceTimeBy()` test that virtual time works

## Why

Required for testing the health-aware await loop in AgentFacadeImpl and PartExecutorImpl with virtual time. `runTest` + `TestCoroutineScheduler` allow coroutine delays to advance instantly.

## Acceptance Criteria

- Dependency added and resolves
- A minimal proof test using `runTest { advanceTimeBy(1000) }` compiles and passes
- `./test.sh` passes

