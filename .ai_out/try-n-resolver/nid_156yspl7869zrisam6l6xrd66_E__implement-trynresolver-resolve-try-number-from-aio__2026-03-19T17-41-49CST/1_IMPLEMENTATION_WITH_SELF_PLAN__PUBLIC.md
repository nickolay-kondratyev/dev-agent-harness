# Implementation Complete: TryNResolver

## What Was Done

Implemented `TryNResolver` interface and `TryNResolverImpl` class that resolves the next try-N number by scanning `.ai_out/` directories per the spec in `doc/core/git.md` lines 78-91.

### Files Created

1. **`app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/TryNResolver.kt`**
   - `TryNResolver` interface with `suspend fun resolve(ticketData: TicketData): Int`
   - `TryNResolverImpl` class with constructor `(repoRoot: Path)`
   - Algorithm: iterates candidateN from 1, builds branch name via `BranchNameBuilder.build()`, checks if `.ai_out/{branchName}/` directory exists, returns first N where it does not

2. **`app/src/test/kotlin/com/glassthought/shepherd/core/git/TryNResolverTest.kt`**
   - 5 test scenarios using BDD GIVEN/WHEN/THEN with `AsgardDescribeSpec`
   - Uses `Files.createTempDirectory` for filesystem isolation
   - Covers: no dirs, try-1 exists, gap detection, consecutive tries, unrelated ticket dirs

## Design Decisions

- Interface + Impl in same file (per project conventions)
- Placed in `com.glassthought.shepherd.core.supporting.git` package alongside `BranchNameBuilder`
- Defines `AI_OUT_DIR` constant locally rather than taking `AiOutputStructure` dependency (simpler, only needs path check)
- Uses `Files.isDirectory()` for existence check (correct behavior: only directories count)
- `FIRST_TRY_NUMBER = 1` named constant (no magic numbers)

## Tests

All tests pass (`./gradlew :app:test` — BUILD SUCCESSFUL).
