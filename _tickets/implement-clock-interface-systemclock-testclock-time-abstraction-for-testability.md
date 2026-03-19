---
id: nid_xeq8q9q7xmr56x5ttr98br4z9_E
title: "Implement Clock interface + SystemClock + TestClock — time abstraction for testability"
status: open
deps: []
links: []
created_iso: 2026-03-19T00:09:49Z
status_updated_iso: 2026-03-19T00:09:49Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [time, testability, infrastructure]
---

## Context

The health-aware await loop in AgentFacadeImpl (ref.ap.9h0KS4EOK5yumssRCJdbq.E) needs wall-clock time
for `lastActivityTimestamp` age comparisons. A `Clock` interface enables deterministic unit tests via
`TestClock` + `advanceTimeBy()`, while production uses `SystemClock`.

Spec reference: ref.ap.whDS8M5aD2iggmIjDIgV9.E (defined in AgentFacade.md and TicketShepherdCreator.md).

## What to Implement

### Interface: `Clock`
- Package: `com.glassthought.shepherd.core.time`
- Single method: `fun now(): Instant` (java.time.Instant)
- KDoc: link to ref.ap.whDS8M5aD2iggmIjDIgV9.E

### Production: `SystemClock`
- Implements `Clock`
- `now()` returns `Instant.now()`
- Stateless — can be a singleton or simple class

### Test double: `TestClock`
- Implements `Clock`
- Constructor takes optional `initialTime: Instant` (defaults to some fixed epoch)
- `advance(duration: Duration)` — advances internal time by the given duration
- `set(instant: Instant)` — sets internal time to a specific instant
- `now()` returns the current internal time
- NOT thread-safe (tests are single-threaded coroutine scope)

### Tests
- `SystemClock.now()` returns a time close to `Instant.now()` (within 1 second tolerance)
- `TestClock` starts at initial time
- `TestClock.advance()` moves time forward
- `TestClock.set()` sets exact time

## Files to create
- `app/src/main/kotlin/com/glassthought/shepherd/core/time/Clock.kt` (interface + SystemClock)
- `app/src/test/kotlin/com/glassthought/shepherd/core/time/TestClock.kt` (test double)
- `app/src/test/kotlin/com/glassthought/shepherd/core/time/ClockTest.kt` (tests)

## Dependencies
- None — standalone utility

