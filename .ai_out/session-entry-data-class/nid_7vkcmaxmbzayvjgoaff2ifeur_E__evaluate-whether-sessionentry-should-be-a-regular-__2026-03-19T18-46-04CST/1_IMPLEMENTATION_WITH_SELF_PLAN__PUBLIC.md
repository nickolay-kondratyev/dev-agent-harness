# SessionEntry: data class -> class

## What was done
- Changed `data class SessionEntry` to `class SessionEntry` in `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt`.
- This removes misleading auto-generated `equals`/`hashCode`/`copy`/`toString` that used reference equality for mutable concurrent fields (`AtomicReference`, `CompletableDeferred`, `ConcurrentLinkedQueue`).
- No consumers of `copy`, `equals`, `hashCode`, or destructuring existed in the codebase.

## Verification
- All existing tests pass (`./gradlew :app:test` — exit code 0).

## Files modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` — single keyword change.
