---
id: nid_0of6zl2493ctvmy9m23kxnhnl_E
title: "Implement HealthTimeoutLadder + Refactor HarnessTimeoutConfig"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T17:36:03Z
status_updated_iso: 2026-03-19T17:04:27Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [health-monitoring, config, refactor]
---

## Context

The spec `doc/use-case/HealthMonitoring.md` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) defines a `HealthTimeoutLadder` data class that groups the three health monitoring timeouts into a cohesive object. The spec change was done (closed ticket nid_7wb6rh7rsz2el1n72x3un3fhg_E), but the **code** in `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` still uses flat fields.

## What Needs to Change

1. Create `HealthTimeoutLadder` data class:
```kotlin
data class HealthTimeoutLadder(
    val startup: Duration = 3.minutes,
    val normalActivity: Duration = 30.minutes,
    val pingResponse: Duration = 3.minutes
)
```

2. Refactor `HarnessTimeoutConfig`:
   - Replace flat fields `startupAckTimeout`, `noActivityTimeout`, `pingTimeout` with `healthTimeouts: HealthTimeoutLadder`
   - Keep `healthCheckInterval` as a separate field (polling interval, not a timeout ladder step)
   - Update `forTests()` to use `HealthTimeoutLadder(startup = 1.second, normalActivity = 5.seconds, pingResponse = 1.second)`

3. Update `HarnessTimeoutConfigTest` to reflect the nested structure.

4. Update any existing callers referencing the old flat field names.

## Files to Modify
- `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfigTest.kt`
- Any files referencing `startupAckTimeout`, `noActivityTimeout`, `pingTimeout` directly

## Acceptance Criteria
- HealthTimeoutLadder data class exists with startup, normalActivity, pingResponse fields
- HarnessTimeoutConfig.healthTimeouts: HealthTimeoutLadder replaces the 3 flat fields
- forTests() creates a fast HealthTimeoutLadder
- All existing tests pass
- No old field names remain in code

