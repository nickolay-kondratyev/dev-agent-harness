---
spec: "com.glassthought.shepherd.core.state.CurrentStatePersistenceTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a CurrentState with execution parts
  - AND WHEN flushing includes all expected fields
    - [PASS] THEN JSON contains status field
  - AND WHEN reading back flushed file
    - [PASS] THEN file contains valid JSON that deserializes to equal CurrentState
  - WHEN flushing to disk
    - [PASS] THEN creates current_state.json at expected path
- GIVEN a state created by CurrentStateInitializer
  - WHEN init → flush → read back
    - [PASS] THEN round-tripped state equals original
- GIVEN a state with null sessionIds
  - WHEN flushing
    - [PASS] THEN JSON does not contain sessionIds key
- GIVEN a successful flush
  - WHEN checking the harness_private directory after flush
    - [PASS] THEN no .tmp files remain
- GIVEN an existing current_state.json
  - WHEN flushing updated state
    - [PASS] THEN file reflects the updated state
