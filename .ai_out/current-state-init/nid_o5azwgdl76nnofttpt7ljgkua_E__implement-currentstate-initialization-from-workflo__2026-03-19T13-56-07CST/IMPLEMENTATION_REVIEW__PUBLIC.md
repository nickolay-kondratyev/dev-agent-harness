# Implementation Review: CurrentState Initialization + Persistence

## Summary

This change adds four capabilities to the `CurrentState` system: (1) initialization from `WorkflowDefinition`, (2) atomic persistence to disk, (3) in-memory mutation methods for status/iteration/sessions, and (4) derived status queries. 67 new tests, all passing. The implementation is clean, well-structured, and closely follows the spec.

**Overall assessment**: Good implementation. One important DRY violation to address, one correctness concern, and a few smaller suggestions.

---

## IMPORTANT Issues

### 1. DRY Violation: Duplicated State Machine in `CurrentState.validateStatusTransition`

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt` (lines 145-171)

The `validateStatusTransition` method in `CurrentState.companion` re-implements the state machine rules that are already authoritatively defined in `SubPartStateTransition` (ref.ap.EHY557yZ39aJ0lV00gPGF.E). The `SubPartStateTransition` sealed class + `SubPartStatus.validateCanSpawn()` + `SubPartStatus.transitionTo()` are explicitly documented as "one authoritative place to audit the state machine."

Having a second implementation means:
- If someone adds a new transition (e.g., `FAILED -> IN_PROGRESS` for retry in V2), they must update both places or the system diverges silently.
- The two implementations already differ subtly: `SubPartStateTransition.IterateContinue` allows `IN_PROGRESS -> IN_PROGRESS`, while `validateStatusTransition` would reject it. This works today because `updateSubPartStatus` is never called for that path (iteration goes through `incrementIteration`), but it is fragile.

**Suggested fix**: Delete `validateStatusTransition` and `VALID_FROM_IN_PROGRESS` from `CurrentState.companion`. Instead, derive validation from the existing state machine. For example, add a simple validation method on `SubPartStatus` that checks raw `(from, to)` pairs using the same source of truth, or reuse the existing `validateCanSpawn`/`transitionTo` logic. The key point is: one source of truth for the state machine.

### 2. `incrementIteration` Does Not Guard Against Exceeding `max`

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt` (line 67)

`incrementIteration` will happily set `current` beyond `max`. While the caller (PartExecutor) may enforce this, the mutation method itself has no guard. If any caller forgets, `current` silently exceeds `max`. This is the kind of invariant that should be enforced at the data mutation layer.

**Suggested fix**: Add a check: `check(iteration.current < iteration.max) { "iteration.current (${iteration.current}) already at max (${iteration.max})" }`.

---

## Suggestions

### 1. `AtomicMoveNotSupportedException` Is Unhandled in `CurrentStatePersistenceImpl`

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentStatePersistence.kt` (line 39)

`ATOMIC_MOVE` will throw `AtomicMoveNotSupportedException` on file systems that don't support it. On Linux with a standard filesystem this won't happen, but if the repo is on NFS or a FUSE mount, it will fail hard with no fallback. This is a low-risk issue for the current deployment, but worth noting. A common pattern is to catch `AtomicMoveNotSupportedException` and fall back to `REPLACE_EXISTING` only.

### 2. `CurrentState` Is Accumulating Responsibilities

`CurrentState` was originally a simple `data class` wrapping `MutableList<Part>`. It now has mutations, validation, derived queries, and helper methods. This is acceptable for now given it is a single cohesive state object, but be watchful as more methods accumulate. If it grows further, consider extracting mutations into a `CurrentStateMutator` class and queries into a `CurrentStateQueries` class.

### 3. Test Temp Directory Cleanup

**File**: `app/src/test/kotlin/com/glassthought/shepherd/core/state/CurrentStatePersistenceTest.kt`

The persistence tests create `Files.createTempDirectory()` directories but never clean them up. While the OS will eventually clean `/tmp`, an `afterEach`/`afterSpec` block calling `tempDir.toFile().deleteRecursively()` would be cleaner. Minor.

### 4. Minor: `initializePart` Exposed as `companion` Method

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentStateInitializer.kt` (line 42)

`initializePart` is `public` on the companion for reuse during plan conversion. The KDoc says this but it would benefit from a more explicit `@see` or comment pointing to where it will be reused, so a future reader doesn't think it's dead code.

---

## What Was Done Well

- **Spec alignment is strong**: Straightforward vs. with-planning workflows handled correctly per spec lines 428-452.
- **Atomic flush pattern is correct**: Temp file in same directory + `ATOMIC_MOVE` + `REPLACE_EXISTING` + cleanup on failure. Textbook implementation.
- **Immutability of data classes preserved**: Mutations use `copy()` to create new instances rather than mutating fields.
- **Constructor injection** on `CurrentStatePersistenceImpl` with `AiOutputStructure`. No singletons.
- **Interface + Impl pattern** used consistently.
- **Test quality is high**: BDD structure, one assert per `it` block, good coverage of edge cases (terminal states, invalid names, idempotency of initialization).
- **Anchor points** used correctly for cross-referencing.
- **`flush` is `suspend`**: Consistent with the `Out` logging pattern and coroutine conventions.
- **Path resolution** correctly uses `AiOutputStructure.currentStateJson()` as required by spec.

---

## Verdict

Address the DRY violation (#1 IMPORTANT) and the iteration max guard (#2 IMPORTANT). The rest are optional improvements. No critical issues found.
