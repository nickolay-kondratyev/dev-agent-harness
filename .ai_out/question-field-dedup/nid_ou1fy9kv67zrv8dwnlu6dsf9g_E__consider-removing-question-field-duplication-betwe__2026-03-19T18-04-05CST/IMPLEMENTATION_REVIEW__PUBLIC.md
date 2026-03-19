# Implementation Review: Eliminate PendingQuestion and Consolidate UserQuestionContext

## Summary

Clean DRY refactor that removes a genuine duplication. `PendingQuestion` was a thin wrapper around `UserQuestionContext` with a redundant `question` field (identical to `UserQuestionContext.question`). The `session/UserQuestionContext` was a byte-for-byte duplicate of `question/UserQuestionContext`. Both were correctly eliminated.

**Verdict: APPROVE** -- no critical or important issues found.

## Verification Checklist

| Check | Result |
|-------|--------|
| `PendingQuestion.kt` deleted | PASS |
| `session/UserQuestionContext.kt` deleted | PASS |
| `SessionEntry.kt` uses `ConcurrentLinkedQueue<UserQuestionContext>` from `core.question` | PASS |
| `SessionEntryTest.kt` updated, no stale `PendingQuestion` references | PASS |
| `doc/core/SessionsState.md` updated, no stale `PendingQuestion` references | PASS |
| `doc/core/UserQuestionHandler.md` checked, no stale references | PASS (none existed) |
| No remaining `PendingQuestion` references in source code | PASS (only in tickets, changelogs, `.ai_out/`) |
| No remaining `core.session.UserQuestionContext` imports in source | PASS (only in `.ai_out/`) |
| `sanity_check.sh` passes | PASS |
| `./test.sh` passes (BUILD SUCCESSFUL, detekt clean) | PASS |
| All existing test cases preserved (no behavior-capturing tests removed) | PASS |

## No CRITICAL Issues

## No IMPORTANT Issues

## Suggestions

1. **Minor doc loss**: The deleted `session/UserQuestionContext` KDoc referenced `ref.ap.NE4puAzULta4xlOLh5kfD.E` (UserQuestionHandler spec). The canonical `question/UserQuestionContext` does not carry this cross-reference. Consider adding it to the KDoc of `question/UserQuestionContext.kt` for discoverability:

   File: `app/src/main/kotlin/com/glassthought/shepherd/core/question/UserQuestionContext.kt`
   ```kotlin
   /**
    * Context provided to [UserQuestionHandler] when an agent asks a question.
    *
    * Contains enough information to identify which agent asked and to display
    * meaningful context to the human operator.
    *
    * See ref.ap.NE4puAzULta4xlOLh5kfD.E for the UserQuestionHandler spec.
    */
   ```

## Documentation Updates Needed

None required beyond the optional suggestion above.
