---
id: nid_89bw63qr6qyewthjq4wp3x0so_E
title: "Implement MutableSynchronizedMap — suspend-friendly Mutex-backed concurrent map"
status: open
deps: []
links: []
created_iso: 2026-03-19T00:38:57Z
status_updated_iso: 2026-03-19T00:38:57Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, sessions-state, foundational]
---

## Context

Spec: `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E), section "Concurrency".

`SessionsState` needs a coroutine-safe map implementation. This ticket implements `MutableSynchronizedMap` — a thin wrapper around `MutableMap` guarded by a `kotlinx.coroutines.sync.Mutex`.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/util/MutableSynchronizedMap.kt`

```kotlin
class MutableSynchronizedMap<K, V> {
    private val mutex = Mutex()
    private val backing = mutableMapOf<K, V>()

    suspend fun get(key: K): V?
    suspend fun put(key: K, value: V): V?
    suspend fun remove(key: K): V?
    suspend fun removeAll(predicate: (K, V) -> Boolean): List<V>
    suspend fun values(): List<V>  // snapshot copy
    suspend fun size(): Int
}
```

All operations acquire the Mutex, perform the underlying map operation, then release.
`removeAll` takes a predicate and removes all matching entries, returning removed values.
`values()` returns a snapshot (copy) to avoid concurrent modification.

## Key Design Points

- Uses `Mutex` (NOT `synchronized`) — coroutine-friendly, does not block threads
- All methods are `suspend` functions
- Thin wrapper — no business logic, just concurrency safety
- Generic — reusable beyond SessionsState

## Tests (BDD/DescribeSpec)

- GIVEN empty map WHEN get(key) THEN returns null
- GIVEN map with entry WHEN get(key) THEN returns value
- GIVEN map WHEN put(key, value) THEN stores and returns previous value
- GIVEN map with entries WHEN removeAll matching predicate THEN removes matching, returns them
- GIVEN map with entries WHEN removeAll non-matching predicate THEN removes nothing
- GIVEN map with entries WHEN values() THEN returns snapshot copy
- Concurrent access test: launch multiple coroutines doing put/remove, verify no corruption

## Package
`com.glassthought.shepherd.core.util`

## Acceptance Criteria
- MutableSynchronizedMap compiles and passes all tests
- All operations are suspend functions using Mutex
- `./test.sh` passes

