---
spec: "com.glassthought.shepherd.coroutines.VirtualTimeProofTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN kotlinx-coroutines-test virtual time
  - WHEN advanceTimeBy is called with 1000ms
    - [PASS] THEN currentTime reflects the advanced virtual time
