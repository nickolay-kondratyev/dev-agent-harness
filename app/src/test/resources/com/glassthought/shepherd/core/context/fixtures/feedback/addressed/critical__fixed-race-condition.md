# Race condition in session manager

**File(s):** `src/main/kotlin/SessionManager.kt`

The session manager has a TOCTOU race condition.

---

## Resolution: ADDRESSED
Fixed by adding mutex around session lookup.
