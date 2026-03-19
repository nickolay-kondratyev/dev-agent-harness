# Implementation: Eliminate PendingQuestion and Consolidate UserQuestionContext

## Summary

Removed `PendingQuestion` wrapper class and the duplicate `UserQuestionContext` in `core/session/`.
The canonical `UserQuestionContext` in `core/question/` is now the single definition used everywhere.

## Changes Made

### Deleted Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/PendingQuestion.kt` — redundant wrapper; its `question` field duplicated what `UserQuestionContext.question` already carried
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/UserQuestionContext.kt` — identical duplicate of `core/question/UserQuestionContext`

### Modified Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` — `questionQueue` type changed from `ConcurrentLinkedQueue<PendingQuestion>` to `ConcurrentLinkedQueue<UserQuestionContext>` (imported from `core.question`)
- `app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionEntryTest.kt` — replaced `createTestPendingQuestion()` helper with `createTestUserQuestionContext()` that constructs `UserQuestionContext` directly
- `doc/core/SessionsState.md` — removed `PendingQuestion` code block and section header; updated table and prose to reference `UserQuestionContext` directly

## Testing
- All tests pass (`./test.sh` — BUILD SUCCESSFUL, detekt clean)

## Notes
- `doc/core/UserQuestionHandler.md` was checked — no `PendingQuestion` references found, no changes needed
- No other source files imported `PendingQuestion` or `core.session.UserQuestionContext`
