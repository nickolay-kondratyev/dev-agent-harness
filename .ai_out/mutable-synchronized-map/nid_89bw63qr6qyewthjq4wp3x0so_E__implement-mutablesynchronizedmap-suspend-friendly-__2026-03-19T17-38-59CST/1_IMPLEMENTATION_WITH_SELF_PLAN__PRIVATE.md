# Implementation: MutableSynchronizedMap

## Status: COMPLETED

## Plan
1. [x] Create `com.glassthought.shepherd.core.util` package
2. [x] Implement `MutableSynchronizedMap<K, V>` with Mutex-backed operations
3. [x] Write comprehensive BDD tests
4. [x] Run full test suite — all green

## Key Decisions
- `put` returns `V?` (previous value) — differs from asgard's version which returns `Unit`
- `removeAll` takes `(K, V) -> Boolean` predicate — differs from asgard's which takes `List<K>`
- No `@ThreadSafe` annotation (asgard-specific)
- Kept it minimal: only the 6 operations specified in the ticket

## Files Created
- `app/src/main/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMap.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMapTest.kt`
