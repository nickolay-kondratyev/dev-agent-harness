---
spec: "com.glassthought.shepherd.core.session.SessionsStateTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN SessionsState with a registered session
  - WHEN lookup(guid)
    - [PASS] THEN returns the registered SessionEntry
- GIVEN SessionsState with a registered session AND same guid re-registered
  - WHEN register(same guid, new entry)
    - [PASS] THEN lookup does not return the original entry
    - [PASS] THEN lookup returns the new entry
- GIVEN empty SessionsState
  - WHEN lookup(guid)
    - [PASS] THEN returns null
- GIVEN multiple sessions for same part
  - WHEN removeAllForPart("partA")
    - [PASS] THEN removes all sessions for that part
    - [PASS] THEN returns both removed entries
    - [PASS] THEN sessions for other parts remain
- GIVEN no sessions for partX
  - WHEN removeAllForPart("partX")
    - [PASS] THEN returns empty list
- GIVEN sessions for partA
  - WHEN removeAllForPart("partA")
    - [PASS] THEN returns the removed entries
- GIVEN sessions for partA and partB
  - WHEN removeAllForPart("partA")
    - [PASS] THEN partA session is removed
    - [PASS] THEN partB session is still present
