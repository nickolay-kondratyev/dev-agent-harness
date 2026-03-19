# MutableSynchronizedMap Implementation

## Summary
Implemented `MutableSynchronizedMap<K, V>` — a suspend-friendly, Mutex-backed concurrent map in `com.glassthought.shepherd.core.util`.

## Files Created

### Source
- `app/src/main/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMap.kt`
  - Operations: `get`, `put` (returns previous value), `remove`, `removeAll` (predicate-based), `values` (snapshot), `size`
  - All operations acquire `Mutex` via `withLock`

### Tests
- `app/src/test/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMapTest.kt`
  - 14 test cases covering all operations, edge cases, and concurrent access
  - BDD style with `AsgardDescribeSpec`, one assert per `it` block

## Test Results
- `./gradlew :app:test` — all tests pass (exit code 0)
