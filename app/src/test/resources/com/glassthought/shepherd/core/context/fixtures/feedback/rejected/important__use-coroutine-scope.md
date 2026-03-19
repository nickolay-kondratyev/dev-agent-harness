# Use CoroutineScope instead of GlobalScope

**File(s):** `src/main/kotlin/Launcher.kt`

GlobalScope should be replaced with structured concurrency.

---

## Resolution: REJECTED
WHY-NOT: GlobalScope is intentional here — this is the top-level launcher that outlives all child coroutines.
