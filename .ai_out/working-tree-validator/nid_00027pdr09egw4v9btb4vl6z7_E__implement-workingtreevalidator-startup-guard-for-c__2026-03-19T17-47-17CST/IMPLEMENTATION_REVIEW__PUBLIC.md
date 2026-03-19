# Implementation Review: WorkingTreeValidator

## VERDICT: PASS_WITH_SUGGESTIONS

---

## Summary

WorkingTreeValidator implements a startup guard that validates the git working tree is clean before any git operations. The implementation follows the GitBranchManager pattern (interface+impl in same file, companion `standard()` factory, `workingDir: Path?`, private `gitCommand()` helper). The error message matches the spec format. All 9 tests pass with BDD structure, one-assert-per-test, and proper GIVEN/WHEN/THEN nesting.

No critical or important issues found. The implementation is correct, well-tested, and follows project standards.

---

## Blocking Issues

None.

---

## Suggestions

### 1. DRY: `gitCommand()` helper is duplicated across git classes

The private `gitCommand(vararg args: String): Array<String>` method is now copy-pasted identically in:
- `GitBranchManagerImpl` (`GitBranchManager.kt` line 92)
- `WorkingTreeValidatorImpl` (`WorkingTreeValidator.kt` line 72)

This is a minor DRY violation. As more git-wrapping classes are added, this will keep getting duplicated. Consider extracting a shared `GitCommandBuilder` utility (or similar) that both classes can compose in. Not blocking -- two occurrences is tolerable -- but worth a follow-up ticket if a third git class is added.

### 2. DRY: Three separate ProcessRunner fakes in test codebase

There are now three distinct `ProcessRunner` test doubles:
1. `FakeProcessRunner` in `app/src/test/.../noninteractive/FakeProcessRunner.kt` -- supports `runProcessV2` only
2. `FakeProcessRunner` (internal) in `GitOperationFailureUseCaseImplTest.kt` -- supports `runProcess` with command-keyed responses
3. `FakeGitStatusProcessRunner` (private) in `WorkingTreeValidatorTest.kt` -- supports `runProcess` for a single hardcoded command

The new `FakeGitStatusProcessRunner` is narrowly scoped and simple, which is fine for this test file. However, the `FakeProcessRunner` in `GitOperationFailureUseCaseImplTest.kt` already implements a general-purpose command-keyed `runProcess` fake that could serve the same purpose. Worth noting for future consolidation -- not blocking for this PR since the fake is private and focused.

### 3. Test: validator is re-constructed in each `it` block for dirty tree scenarios

In the "dirty working tree with mixed changes" describe block, the same validator is constructed 3 times (once per `it` block). This is correct per the one-assert-per-test pattern and avoids shared mutable state, so it is the right approach. No change needed -- just noting this is intentional and appropriate.

---

## Positive Observations

1. **Spec compliance**: Error message matches the spec format exactly, including "Working tree is not clean", "Dirty files:" section, and "Please commit or stash your changes before running 'shepherd run'."
2. **Pattern consistency**: Follows GitBranchManager pattern precisely -- interface+impl in same file, companion `standard()`, constructor shape, `gitCommand()` helper, `Out` logging.
3. **Test coverage**: 9 tests cover clean (empty), clean (whitespace), dirty (modified), dirty (untracked), and dirty (mixed). Each assert is isolated in its own `it` block.
4. **Structured logging**: Uses `Out` with `Val`/`ValType` properly. No log-and-throw -- logs a warning, then throws.
5. **detekt-baseline.xml**: Only added the expected `SpreadOperator` suppression for the new class, consistent with existing GitBranchManager entries.
6. **`buildErrorMessage` extracted to companion**: Good testability seam, clearly documented with WHY comment.
7. **`logCheckOverrideAllow(LogLevel.WARN)`**: Properly allows the expected WARN log in dirty-tree tests instead of masking it.

---

## Documentation Updates Needed

None. The spec at `doc/core/git.md` already covers this component. No CLAUDE.md changes required.
