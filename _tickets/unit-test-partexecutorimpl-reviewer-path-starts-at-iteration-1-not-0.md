---
closed_iso: 2026-03-20T19:07:09Z
id: nid_m2si3js0u72ugb4yfamk0u74y_E
title: "Unit test: PartExecutorImpl reviewer path starts at iteration 1 (not 0)"
status: closed
deps: []
links: []
created_iso: 2026-03-20T18:43:36Z
status_updated_iso: 2026-03-20T19:07:09Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [unit-test, regression-guard]
---

PartExecutorImpl conditionally shifts to 1-based iteration counting when reviewerConfig is present (lines 79-87). No test explicitly verifies this.

## What to add
In `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt`:

1. Test that reviewer first cycle uses currentIteration=1 in SubPartDoneContext/CommitMessageBuilder
2. Test that doer-only path stays at currentIteration=0
3. Verify commit message format is "(iteration 1/N)" on first reviewer cycle

The implementation has:
```kotlin
private var currentIteration: Int =
    if (reviewerConfig \!= null) iterationConfig.current + 1 else iterationConfig.current
```

## Context
Bug 4 of the E2E fix (commit febed371). The dual convention (0-based doer-only, 1-based reviewer) is tricky and needs explicit tests. Existing tests exercise iteration flows but don't verify the starting value.

