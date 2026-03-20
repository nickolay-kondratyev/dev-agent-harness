---
id: nid_j1ovm6ohu22o5y8swg2d2hjyz_E
title: "Unit test: buildStartCommand includes [HARNESS_GUID] in bootstrap message"
status: in_progress
deps: []
links: []
created_iso: 2026-03-20T18:43:27Z
status_updated_iso: 2026-03-20T18:53:44Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [unit-test, regression-guard]
---

ClaudeCodeAdapterTest verifies the command contains the bootstrap message (line 81) but does NOT verify that [HARNESS_GUID: {guid}] is appended to it.

Implementation at ClaudeCodeAdapter.kt lines 117-118:
```kotlin
val bootstrapWithGuid = "${params.bootstrapMessage}\n\n" +
    "[HARNESS_GUID: ${params.handshakeGuid.value}]"
```

## What to add
In `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapterTest.kt`, add:
```kotlin
it("THEN command contains [HARNESS_GUID: {guid}] appended to bootstrap message") {
    command shouldContain "[HARNESS_GUID: ${testGuid.value}]"
}
```

## Context
Bug 3 of the E2E fix (commit febed371) added the GUID marker to the bootstrap message so FilesystemGuidScanner can find it in JSONL content. Without this test, someone could remove the marker and only the E2E test (12+ min) would catch it.

