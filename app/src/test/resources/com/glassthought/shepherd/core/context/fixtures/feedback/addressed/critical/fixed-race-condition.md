# Race condition in session manager

**File(s):** `src/main/kotlin/SessionManager.kt`

The session manager has a TOCTOU race condition.

---

## Movement Log

### [2026-03-14T10:00:00Z] Moved by: impl | From: unaddressed/critical → To: addressed/critical
Fixed by adding mutex around session lookup.
