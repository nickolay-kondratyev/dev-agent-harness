# Implementation Review: MutableSynchronizedMap

## Summary

Implementation of `MutableSynchronizedMap<K, V>` -- a suspend-friendly, Mutex-backed concurrent map in `com.glassthought.shepherd.core.util`. The implementation is clean, minimal, and correct. All 6 required operations are implemented. All ticket-specified test cases are covered plus additional edge cases. `./test.sh` and `./sanity_check.sh` both pass.

**Overall Assessment**: Solid, focused implementation that follows the ticket spec faithfully.

## Checklist

| Requirement | Status |
|---|---|
| `get(key: K): V?` | Implemented |
| `put(key: K, value: V): V?` (returns previous) | Implemented |
| `remove(key: K): V?` | Implemented |
| `removeAll(predicate: (K, V) -> Boolean): List<V>` | Implemented |
| `values(): List<V>` (snapshot) | Implemented |
| `size(): Int` | Implemented |
| All operations use `mutex.withLock` | Confirmed |
| All operations are `suspend` | Confirmed |
| BDD test style with one assert per test | Confirmed |
| Concurrent access test | Confirmed |
| `./test.sh` passes | Confirmed |

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. Concurrent test does not exercise real concurrency

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMapTest.kt`, lines 179-210

The concurrent access test uses `runTest`, which runs coroutines on a single-threaded `TestCoroutineScheduler`. All `launch` calls execute sequentially -- the Mutex is never actually contended. This test validates logical correctness of sequential put-then-remove, but does NOT validate concurrency safety.

To exercise real parallelism, use `Dispatchers.Default` with `coroutineScope`:

```kotlin
it("THEN map is not corrupted by concurrent put and remove operations") {
  val map = MutableSynchronizedMap<Int, Int>()
  val coroutineCount = 100

  runBlocking(Dispatchers.Default) {
    // Launch coroutines that put values concurrently
    (0 until coroutineCount).map { i ->
      launch { map.put(i, i * 10) }
    }.forEach { it.join() }
  }

  runBlocking {
    map.size() shouldBe coroutineCount

    runBlocking(Dispatchers.Default) {
      (0 until coroutineCount).filter { it % 2 == 0 }.map { i ->
        launch { map.remove(i) }
      }.forEach { it.join() }
    }

    map.size() shouldBe coroutineCount / 2

    val values = map.values()
    values shouldContainExactlyInAnyOrder (0 until coroutineCount).filter { it % 2 != 0 }.map { it * 10 }
  }
}
```

**Verdict**: SHOULD-FIX. The Mutex implementation is correct by construction, so this is not a correctness risk. But the test's name promises concurrent testing that it doesn't deliver. Either fix the test to use real concurrency, or rename the test to clarify it validates logical correctness only.

### 2. `removeAll` -- minor inefficiency iterating entries twice

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMap.kt`, lines 37-43

Current implementation:
```kotlin
val toRemove = backing.entries.filter { predicate(it.key, it.value) }
val removedValues = toRemove.map { it.value }
toRemove.forEach { backing.remove(it.key) }
```

This iterates `toRemove` twice (once for values, once for removal). A single-pass approach using `iterator().remove()` would be cleaner and avoid holding references to `Map.Entry` objects after removal:

```kotlin
suspend fun removeAll(predicate: (K, V) -> Boolean): List<V> =
  mutex.withLock {
    val removedValues = mutableListOf<V>()
    val iterator = backing.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (predicate(entry.key, entry.value)) {
        removedValues.add(entry.value)
        iterator.remove()
      }
    }
    removedValues
  }
```

**Verdict**: NIT. The current approach is correct and clear. The inefficiency is negligible for typical map sizes. Only suggesting for idiomatic improvement.

## Suggestions

### 1. The "GIVEN empty map" describe block shares a map instance

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMapTest.kt`, lines 13-27

The `map` val at line 14 is shared between the `get` and `size` tests. This works because neither test mutates the map, but it creates a fragile pattern -- if someone later adds a mutating test under this `describe`, it will cause shared-state pollution. Other `describe` blocks correctly create fresh maps inside each `it` block. Consider following the same pattern here for consistency.

### 2. Test for `remove` on key that was never added reads well

Good job separating the `remove` tests into "key exists" and "key does not exist" branches with separate assertions for return value and map state. This is clean BDD.

## Documentation Updates Needed

None required.

## Verdict

**APPROVED** -- The implementation is correct, well-tested, and follows project standards. The concurrent test improvement (IMPORTANT #1) is recommended but not blocking since the Mutex provides correctness by construction.
