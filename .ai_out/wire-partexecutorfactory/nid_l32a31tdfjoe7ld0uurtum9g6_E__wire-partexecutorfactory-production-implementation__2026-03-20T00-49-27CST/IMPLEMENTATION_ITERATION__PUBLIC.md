# Implementation Iteration: Reviewer Feedback (S1 + S2)

## S1: Replace hardcoded `System.getProperty("user.dir")` with injectable `repoRoot`

### Problem
`ProductionPartExecutorFactoryCreator.buildGitCommitStrategy()` used `System.getProperty("user.dir")` to construct the `.git` directory path, making it non-injectable and untestable in isolation.

### Changes
1. **`PartExecutorFactoryCreator.kt`** -- Added `repoRoot: Path` field to `PartExecutorFactoryContext` data class.
2. **`ProductionPartExecutorFactoryCreator.kt`** -- `buildGitCommitStrategy()` now accepts a `repoRoot: Path` parameter and uses `repoRoot.resolve(".git")` instead of `Path.of(System.getProperty("user.dir"), ".git")`.
3. **`TicketShepherdCreator.kt`** -- Passes `repoRoot = repoRoot` (already a constructor parameter of `TicketShepherdCreatorImpl`) when constructing `PartExecutorFactoryContext`.

### Files modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactoryCreator.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/ProductionPartExecutorFactoryCreator.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`

---

## S2: Eliminate `DOER_INDEX` / `REVIEWER_INDEX` constant duplication

### Problem
`DOER_INDEX = 0` was defined as a private constant in both `SubPartConfigBuilder.kt` and `PartExecutorFactoryCreator.kt`. `REVIEWER_INDEX = 1` was also duplicated in `PartExecutorFactoryCreator.kt`. The canonical index-to-role mapping lives in `SubPartRole.fromIndex()`, so the constants belong there.

### Changes
1. **`SubPartRole.kt`** -- Added `const val DOER_INDEX = 0` and `const val REVIEWER_INDEX = 1` as public constants on `SubPartRole.companion`. Updated `fromIndex()` to use these constants instead of raw literals.
2. **`SubPartConfigBuilder.kt`** -- Removed `private const val DOER_INDEX = 0` from companion. Uses `SubPartRole.DOER_INDEX` instead.
3. **`PartExecutorFactoryCreator.kt`** -- Removed `private const val DOER_INDEX = 0` and `private const val REVIEWER_INDEX = 1` from companion. Uses `SubPartRole.DOER_INDEX` and `SubPartRole.REVIEWER_INDEX` instead.

### Files modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartRole.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilder.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactoryCreator.kt`

---

## Test Results

All tests pass (`./gradlew :app:test` -- EXIT_CODE=0). No test changes were required since no tests directly construct `PartExecutorFactoryContext` (tests use lambda stubs for `PartExecutorFactoryCreator`).
