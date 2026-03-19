# Private Review Notes: SessionEntry + PendingQuestion

## Review Process

1. Read all 4 changed files (3 production, 1 test)
2. Read spec documents: `SessionsState.md`, `UserQuestionHandler.md`
3. Verified existing `PayloadId` and `SubPartRole` were reused correctly
4. Checked git diff: all new files, no removals or modifications to existing code
5. Ran `./sanity_check.sh` -- passed
6. Ran `SessionEntryTest` specifically -- all 6 tests passed

## Key Findings

### The `question` duplication is the most actionable issue

Both `PendingQuestion.question` and `UserQuestionContext.question` carry the same value. This is a spec-originated DRY violation. The implementor faithfully followed the spec, so this is not an implementation error -- but it should be flagged for spec correction before more consumers depend on it.

The risk is that someone constructs a `PendingQuestion` where the two `question` fields diverge. The `UserQuestionHandler.handleQuestion(context)` reads from `context.question`, while other code might read `pendingQuestion.question`. If they diverge, you get a silent bug.

### `data class` concern is lower priority

The ticket explicitly specifies `data class`. The concern about `copy()` aliasing is real but the class is internal to `AgentFacadeImpl`/`ShepherdServer`. In practice, `SessionEntry` instances are likely created once, registered, looked up, and removed -- never copied. The risk is theoretical for V1.

### No security concerns

All fields are domain types. No user input handling, no serialization, no file I/O. Pure data holder.

### No lost functionality

All changes are purely additive (new files only). No existing code was modified or removed.

## Verdict

APPROVE. The `question` duplication should be addressed via follow-up ticket (spec + code change together). The `data class` concern can be deferred.
