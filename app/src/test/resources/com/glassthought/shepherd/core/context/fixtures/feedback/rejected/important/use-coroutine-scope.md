# Use CoroutineScope instead of GlobalScope

**File(s):** `src/main/kotlin/Launcher.kt`

GlobalScope should be replaced with structured concurrency.

---

## Movement Log

### [2026-03-14T11:00:00Z] Moved by: impl | From: unaddressed/important → To: rejected/important
WHY-NOT: GlobalScope is intentional here — this is the top-level launcher that outlives all child coroutines.
