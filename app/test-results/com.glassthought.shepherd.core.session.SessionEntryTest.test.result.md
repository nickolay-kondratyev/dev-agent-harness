---
spec: "com.glassthought.shepherd.core.session.SessionEntryTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN SessionEntry with empty questionQueue
  - [PASS] THEN isQAPending is false
- GIVEN SessionEntry with empty queue
  - WHEN question added to queue
    - [PASS] THEN isQAPending becomes true
- GIVEN SessionEntry with non-empty questionQueue
  - [PASS] THEN isQAPending is true
- GIVEN SessionEntry with questions in queue
  - WHEN queue is drained
    - [PASS] THEN isQAPending becomes false
- GIVEN SessionEntry with subPartIndex 0
  - [PASS] THEN role is DOER
- GIVEN SessionEntry with subPartIndex 1
  - [PASS] THEN role is REVIEWER
