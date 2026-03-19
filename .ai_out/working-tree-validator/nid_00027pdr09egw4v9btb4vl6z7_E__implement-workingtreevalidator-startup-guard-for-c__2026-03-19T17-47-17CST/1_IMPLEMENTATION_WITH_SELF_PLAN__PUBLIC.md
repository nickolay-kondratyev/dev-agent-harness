# Implementation: WorkingTreeValidator

## What Was Done

Implemented `WorkingTreeValidator` -- a startup guard that validates the git working tree is clean before any git operations (ref.ap.QL051Wl21jmmYqTQTLglf.E).

### Implementation

- **Interface + Impl** in single file following `GitBranchManager` pattern exactly
- `suspend fun validate()` runs `git status --porcelain` via `ProcessRunner`
- If output is empty (or whitespace-only): succeeds silently
- If output is non-empty: throws `IllegalStateException` with structured error message listing dirty files and instructing user to commit/stash
- Companion `standard()` factory, optional `workingDir: Path?`, private `gitCommand()` helper
- Structured logging via `Out` with `Val`/`ValType`

### Tests (9 tests, all passing)

- Clean tree (empty output) -- succeeds
- Clean tree (whitespace-only output) -- succeeds
- Dirty tree with modified files -- throws, message contains file name
- Dirty tree with untracked files -- throws, message contains file name
- Dirty tree with mixed changes -- throws, message contains commit/stash instruction, "Working tree is not clean", and all dirty file names

## Files Created/Modified

| File | Action |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/WorkingTreeValidator.kt` | Created - interface + impl |
| `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/WorkingTreeValidatorTest.kt` | Created - 9 BDD unit tests |
| `detekt-baseline.xml` | Modified - added SpreadOperator baseline for WorkingTreeValidator |

## Test Results

All tests pass: `./gradlew :app:test` -- BUILD SUCCESSFUL (760+ tests, 0 failures)
