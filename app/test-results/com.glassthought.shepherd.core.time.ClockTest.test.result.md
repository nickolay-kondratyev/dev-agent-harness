---
spec: "com.glassthought.shepherd.core.time.ClockTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a SystemClock
  - WHEN now() is called
    - [PASS] THEN it returns a time within 1 second of Instant.now()
- GIVEN a TestClock
  - WHEN set() is called after advance()
    - [PASS] THEN now() returns the set instant (overriding previous advance)
  - WHEN set() is called with a specific instant
    - [PASS] THEN now() returns that exact instant
- GIVEN a TestClock at EPOCH
  - WHEN advance is called twice (5 minutes then 2 hours)
    - [PASS] THEN now() returns the cumulative advancement
  - WHEN advance is called with 5 minutes
    - [PASS] THEN now() returns EPOCH + 5 minutes
- GIVEN a TestClock with a custom initial time
  - [PASS] THEN now() returns the custom initial time
- GIVEN a TestClock with default initial time
  - [PASS] THEN now() returns Instant.EPOCH
