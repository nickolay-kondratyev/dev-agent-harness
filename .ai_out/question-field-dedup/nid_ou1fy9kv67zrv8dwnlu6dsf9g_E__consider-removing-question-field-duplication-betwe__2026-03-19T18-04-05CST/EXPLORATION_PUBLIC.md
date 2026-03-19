# Exploration: Question Field Duplication

## Summary
Two identical `UserQuestionContext` classes exist (`core/session/` and `core/question/`). `PendingQuestion` wraps `UserQuestionContext` adding a redundant `question` field that's never directly accessed.

## Approach: Eliminate PendingQuestion, consolidate UserQuestionContext
- Delete `PendingQuestion.kt` and `core/session/UserQuestionContext.kt`
- Keep `core/question/UserQuestionContext.kt` as canonical location (where `UserQuestionHandler` lives)
- Change `SessionEntry.questionQueue` from `ConcurrentLinkedQueue<PendingQuestion>` to `ConcurrentLinkedQueue<UserQuestionContext>`
- Update tests and spec docs

## Risk: LOW
- `PendingQuestion.question` is never directly accessed
- `isQAPending` depends only on queue emptiness
- Queue drain use case (not yet implemented) will work naturally with `UserQuestionContext`

## Files to modify
- DELETE: `app/src/main/kotlin/com/glassthought/shepherd/core/session/PendingQuestion.kt`
- DELETE: `app/src/main/kotlin/com/glassthought/shepherd/core/session/UserQuestionContext.kt`
- MODIFY: `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt`
- MODIFY: `app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionEntryTest.kt`
- MODIFY: `doc/core/SessionsState.md`
- MODIFY: `doc/core/UserQuestionHandler.md` (minor — remove PendingQuestion reference if any)
