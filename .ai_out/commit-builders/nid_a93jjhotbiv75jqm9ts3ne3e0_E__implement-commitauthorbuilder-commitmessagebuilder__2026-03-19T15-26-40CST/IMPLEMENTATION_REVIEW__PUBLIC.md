# Implementation Review: CommitAuthorBuilder & CommitMessageBuilder

## Summary

Two new stateless `object` utilities for building git commit metadata -- author names and commit messages. The implementation correctly follows the spec in `doc/core/git.md`, matches the `BranchNameBuilder` pattern, and all tests pass (including sanity check). The code is clean, well-structured, and uses compiler-enforced exhaustiveness on the `AgentType` enum as required.

**Overall assessment: APPROVE -- no critical or important issues found.**

## Files Reviewed

- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/CommitAuthorBuilder.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/CommitMessageBuilder.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/git/CommitAuthorBuilderTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/git/CommitMessageBuilderTest.kt`

## No CRITICAL Issues

No security, correctness, or data-loss issues found.

## No IMPORTANT Issues

No architecture violations or maintainability concerns.

## Suggestions

### 1. Consider validating `currentIteration <= maxIterations`

In `CommitMessageBuilder.build()`, when `hasReviewer=true`, the code validates that both `currentIteration >= 1` and `maxIterations >= 1`, but does not check that `currentIteration <= maxIterations`. This would allow producing a message like `(iteration 5/3)`, which is semantically nonsensical.

```kotlin
// In CommitMessageBuilder.build(), after existing require blocks for hasReviewer:
require(currentIteration <= maxIterations) {
    "currentIteration [$currentIteration] must not exceed maxIterations [$maxIterations]"
}
```

**Counterargument**: The spec does not explicitly require this validation, and it may be intentionally left loose to avoid coupling the builder to iteration semantics that live elsewhere. The builder's job is formatting, not enforcing iteration policy. This is a judgment call for the implementer.

### 2. Minor: duplicate test in CommitMessageBuilderTest

The test at line 40-48 ("THEN omits iteration info") duplicates the test at lines 14-25 (same inputs, same assertion). Both verify `[shepherd] planning/plan -- completed` with `hasReviewer=false`. The first test already covers this case with the describe block "AND partName='planning', subPartName='plan', result='completed'". The second test at line 40 could be removed without losing coverage, or it could test a different scenario to add value.

```
// Lines 14-25: already tests planning/plan/completed with hasReviewer=false
// Lines 40-48: repeats the exact same test
```

## Spec Compliance Check

| Spec requirement | Status |
|---|---|
| `[shepherd]` prefix | Correct |
| `{part_name}/{sub_part_name} -- {result}` format | Correct |
| Iteration info only when `hasReviewer=true` | Correct |
| `${CODING_AGENT}_${CODING_MODEL}_WITH-${HOST_USERNAME}` format | Correct |
| `ClaudeCode -> CC`, `PI -> PI` mapping | Correct |
| No `else` branch on `when` (compiler exhaustiveness) | Correct |
| Spec examples from `doc/core/git.md` covered in tests | Correct |
| Input validation with `require` | Correct |
| Stateless `object` pattern matching `BranchNameBuilder` | Correct |

## Test Quality

- BDD GIVEN/WHEN/THEN structure: correct
- One assert per `it` block: correct
- Edge cases (blank inputs): covered
- Both `AgentType` values tested: yes
- Multiple spec examples from doc covered: yes
- Validation error paths tested: yes

## Documentation Updates Needed

None.
