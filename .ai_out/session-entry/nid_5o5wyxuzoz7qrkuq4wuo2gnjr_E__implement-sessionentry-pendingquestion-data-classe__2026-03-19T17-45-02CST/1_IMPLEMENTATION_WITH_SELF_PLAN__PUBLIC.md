## Completed: Implement SessionEntry + PendingQuestion data classes

**What was done:**
- Created `UserQuestionContext` data class capturing agent identity and plan position for question routing
- Created `PendingQuestion` data class pairing a question string with its full context
- Created `SessionEntry` data class as the live session registry entry with:
  - Derived `isQAPending` property (backed by `ConcurrentLinkedQueue.isNotEmpty()`)
  - Derived `role` property (delegates to `SubPartRole.fromIndex(subPartIndex)`)
  - `@AnchorPoint("ap.igClEuLMC0bn7mDrK41jQ.E")` annotation
- Added 6 BDD unit tests covering all required behaviors

**Files created:**
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/UserQuestionContext.kt` — agent question context with handshakeGuid, partName, subPartName, subPartRole
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/PendingQuestion.kt` — question + context wrapper
- `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` — live session registry entry with derived properties
- `app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionEntryTest.kt` — 6 BDD tests

**Tests (6/6 passing):**
- GIVEN empty questionQueue THEN isQAPending is false
- GIVEN non-empty questionQueue THEN isQAPending is true
- GIVEN subPartIndex 0 THEN role is DOER
- GIVEN subPartIndex 1 THEN role is REVIEWER
- GIVEN empty queue WHEN question added THEN isQAPending becomes true
- GIVEN questions in queue WHEN queue drained THEN isQAPending becomes false

**Design decisions:**
- Used `object : TmuxCommunicator` with `= Unit` returns for test no-op (avoids detekt EmptyFunctionBlock)
- Used SAM conversion for `SessionExistenceChecker` (fun interface)
- Test helpers are private functions at file scope following existing test patterns
