# Exploration: MutableSynchronizedMap

## Key Findings

### 1. Reference Implementation Exists in Asgard
- `.tmp/commonMain/com/asgard/core/threading/collections/MutableSynchronizedMap.kt` — full Mutex-backed map
- **Differences from ticket spec**: asgard's `put` returns Unit (ticket wants `V?`), asgard's `removeAll` takes key list (ticket wants predicate `(K, V) -> Boolean` returning `List<V>`)

### 2. Package `com.glassthought.shepherd.core.util` Does NOT Exist Yet
- Needs to be created at `app/src/main/kotlin/com/glassthought/shepherd/core/util/`

### 3. Testing Pattern
- Extend `AsgardDescribeSpec`
- BDD: `describe` for GIVEN/WHEN, `it` for THEN
- One assert per `it` block
- `outFactory` inherited from `AsgardDescribeSpec`
- Suspend calls go inside `it` blocks (not `describe`)
- Concurrent tests use `runTest { ... }` with `launch`

### 4. Dependencies Available
- `kotlinx-coroutines-core:1.10.2` (has `Mutex`, `withLock`)
- `kotlinx-coroutines-test` (has `runTest`)

### 5. Spec Reference
- `doc/core/SessionsState.md` references `MutableSynchronizedMap` for coroutine-safe backing
- Operations needed by SessionsState: register (put), lookup (get), removeAllForPart (removeAll with predicate)
