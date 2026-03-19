---
spec: "com.glassthought.shepherd.core.state.SubPartRoleTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN all valid index-to-role mappings
  - WHEN fromIndex(0)
    - [PASS] THEN returns DOER
  - WHEN fromIndex(1)
    - [PASS] THEN returns REVIEWER
- GIVEN invalid sub-part indices
  - WHEN fromIndex(-1)
    - [PASS] THEN throws IllegalArgumentException
  - WHEN fromIndex(2)
    - [PASS] THEN throws IllegalArgumentException
- GIVEN valid sub-part indices
  - WHEN fromIndex(0)
    - [PASS] THEN returns DOER
  - WHEN fromIndex(1)
    - [PASS] THEN returns REVIEWER
