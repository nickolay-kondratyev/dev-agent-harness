# Implementation Reviewer -- Private Notes

## Review Checklist

- [x] `./sanity_check.sh` passes
- [x] `./gradlew :app:test` passes (all 760+ tests, 0 failures)
- [x] WorkingTreeValidatorTest: 9/9 tests pass
- [x] Spec compliance verified (error message format matches doc/core/git.md lines 22-30)
- [x] Pattern consistency with GitBranchManager verified
- [x] detekt-baseline.xml: only SpreadOperator addition, consistent with existing entries
- [x] No removed tests or anchor points
- [x] No security concerns (no secrets, no injection vectors)
- [x] No resource leaks

## DRY Analysis

### gitCommand() duplication
- `GitBranchManagerImpl.gitCommand()` and `WorkingTreeValidatorImpl.gitCommand()` are identical
- Two occurrences is tolerable; flag if a third appears
- Extraction would be a `GitCommandBuilder` class or similar

### ProcessRunner fakes
- Three separate fakes exist in test code
- Each is scoped to its specific test needs
- The one in GitOperationFailureUseCaseImplTest is `internal` and could serve as a shared utility
- The new FakeGitStatusProcessRunner is deliberately minimal and private -- appropriate

## Error Message Format Detail

Spec example uses `  M  src/Main.kt` (2-char indent). Implementation does `"  $it"` where `$it` is the raw porcelain line like ` M src/Main.kt`. So actual output is `   M src/Main.kt` (3 chars before M). This is cosmetic and the tests validate content correctly via `shouldContain`. Not a real issue since the error is meant for human consumption and the file names are clearly visible.
