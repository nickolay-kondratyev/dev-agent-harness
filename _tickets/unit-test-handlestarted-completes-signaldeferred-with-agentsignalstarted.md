---
closed_iso: 2026-03-20T18:56:29Z
id: nid_9koh9x56xefgqrysza3o92f4e_E
title: "Unit test: handleStarted() completes signalDeferred with AgentSignal.Started"
status: closed
deps: []
links: []
created_iso: 2026-03-20T18:43:18Z
status_updated_iso: 2026-03-20T18:56:29Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [unit-test, regression-guard]
---

ShepherdServerTest covers /signal/started HTTP 200 and timestamp update, but does NOT verify that signalDeferred is completed with AgentSignal.Started.

All other signal endpoints have this assertion:
- /signal/done (line 76): signalDeferred.getCompleted() returns AgentSignal.Done
- /signal/fail-workflow (line 270): AgentSignal.FailWorkflow
- /signal/self-compacted (line 434): AgentSignal.SelfCompacted

## What to add
In `app/src/test/kotlin/com/glassthought/shepherd/core/server/ShepherdServerTest.kt`, add:
```kotlin
it("THEN signalDeferred is completed with AgentSignal.Started") {
    runBlocking { entry.signalDeferred.getCompleted() shouldBe AgentSignal.Started }
}
```

Follow the existing pattern for other signal assertions in the same file.

## Context
Bug 2 of the E2E fix (commit febed371) added AgentSignal.Started but only the E2E test exercises this path. A unit test catches regressions much faster.

