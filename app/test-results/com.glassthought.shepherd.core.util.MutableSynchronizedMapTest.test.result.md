---
spec: "com.glassthought.shepherd.core.util.MutableSynchronizedMapTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN concurrent access
  - [PASS] THEN map is not corrupted by concurrent put and remove operations
- GIVEN empty map
  - WHEN get(key)
    - [PASS] THEN returns null
  - WHEN size()
    - [PASS] THEN returns 0
- GIVEN map WHEN put
  - AND key already exists
    - [PASS] THEN overwrites with new value
    - [PASS] THEN returns previous value
  - AND key is new
    - [PASS] THEN returns null (no previous value)
- GIVEN map WHEN remove
  - AND key does not exist
    - [PASS] THEN returns null
  - AND key exists
    - [PASS] THEN entry is no longer present
    - [PASS] THEN returns removed value
- GIVEN map WHEN size()
  - [PASS] THEN returns current entry count
- GIVEN map with entries WHEN removeAll
  - AND predicate matches no entries
    - [PASS] THEN map is unchanged
    - [PASS] THEN returns empty list
  - AND predicate matches some entries
    - [PASS] THEN matched entries are removed from map
    - [PASS] THEN returns matched values
- GIVEN map with entries WHEN values()
  - [PASS] THEN returned list is a snapshot (not affected by subsequent mutations)
  - [PASS] THEN returns snapshot of all values
- GIVEN map with entry
  - WHEN get(key)
    - [PASS] THEN returns value
