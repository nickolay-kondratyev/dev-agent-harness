---
id: nid_qedn4pb3eix0prpawphzikvvn_E
title: "Properly close OkHttpClient in AppDependencies"
status: open
deps: []
links: []
created_iso: 2026-03-09T21:12:22Z
status_updated_iso: 2026-03-09T21:12:22Z
type: task
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [infrastructure, resource-management]
---

## Context

During review of the GLM API implementation, it was identified that OkHttpClient is created but never closed in Initializer.kt. While this is acceptable for CLI usage, the application will be used from a long-running server.

## Problem

The OkHttpClient in InitializerImpl holds connection pools and thread pools that should be properly cleaned up on shutdown.

**File:** app/src/main/kotlin/com/glassthought/initializer/Initializer.kt:72-74

```kotlin
// OkHttpClient lifecycle is tied to process lifetime, which is acceptable
// for a CLI application. For long-running processes, add proper shutdown.
val httpClient = OkHttpClient.Builder()
    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()
```

## Solution

1. Make AppDependencies implement AsgardCloseable
2. Store httpClient in AppDependencies
3. Override close() to call httpClient.dispatcher.executorService.shutdown() and httpClient.connectionPool.evictAll()
4. Ensure the main server calls close() on shutdown

## Acceptance Criteria

- [ ] AppDependencies implements AsgardCloseable
- [ ] OkHttpClient is properly closed when AppDependencies.close() is called
- [ ] Main server (or entry point) calls AppDependencies.use{} or explicitly closes dependencies on shutdown

