# Exploration Summary: Inner Feedback Loop in PartExecutorImpl

## Integration Point
- `PartExecutorImpl.processNeedsIteration()` — current method handles PUBLIC.md validation, budget check, git commit, iteration increment
- **No inner loop yet** — directly returns null to re-enter main while loop
- File: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`

## Dependencies (All Implemented)

| Dependency | File | API |
|-----------|------|-----|
| ReInstructAndAwait | `.../usecase/reinstructandawait/ReInstructAndAwait.kt` | `suspend fun execute(handle, message): ReInstructOutcome` (Responded/FailedWorkflow/Crashed) |
| RejectionNegotiationUseCase | `.../usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt` | `suspend fun execute(doerHandle, reviewerHandle, feedbackFilePath): RejectionResult` (Accepted/AddressedAfterInsistence/AgentCrashed/FailedWorkflow) |
| FeedbackResolutionParser | `.../feedback/FeedbackResolutionParser.kt` | `object parse(fileContent): ParseResult` (Found/MissingMarker/InvalidMarker) |
| AiOutputStructure | `.../filestructure/AiOutputStructure.kt` | `feedbackPendingDir(partName)`, `feedbackAddressedDir(partName)`, `feedbackRejectedDir(partName)` |
| InstructionSection.FeedbackItem | `.../context/InstructionSection.kt` | Renders per-feedback-item doer instructions |
| InstructionSection.FeedbackDirectorySection | `.../context/InstructionSection.kt` | Globs feedback dir files for reviewer context |
| ProtocolVocabulary.SeverityPrefix | `.../context/ProtocolVocabulary.kt` | `CRITICAL="critical__"`, `IMPORTANT="important__"`, `OPTIONAL="optional__"` |

## What Needs to Be Built

### In processNeedsIteration():
1. **Feedback Files Presence Guard (R9)** — empty pending/ → AgentCrashed
2. **Inner Loop (R3)** — process critical → important → optional (sorted by filename within severity)
3. **PROCESS_FEEDBACK_ITEM** for each file:
   - Self-compaction check
   - Assemble doer instructions (single item via ContextForAgentProvider/FeedbackItem)
   - ReInstructAndAwait.execute(doerHandle, instructions)
   - Parse resolution marker
   - ADDRESSED → move to addressed/, git commit
   - SKIPPED (optional only) → move to addressed/, git commit
   - REJECTED → RejectionNegotiationUseCase.execute()
   - Missing/invalid marker → AgentCrashed
4. **Post-loop validation** — no critical/important in pending/ (bug guard)
5. **Reviewer re-instruction** — assemble reviewer instructions with addressed/rejected context
6. **R10** — iteration.current increments once per needs_iteration (already exists), NOT per item

## Key Tests Needed
- Inner loop processes critical → important → optional order
- ADDRESSED → file moved to addressed/
- SKIPPED on optional → file moved to addressed/
- Missing resolution marker → AgentCrashed
- SKIPPED on critical/important → AgentCrashed
- Self-compaction check fires at each done boundary
- iteration.current increments once per needs_iteration (not per item)
- Empty pending → AgentCrashed
- Rejection → delegates to RejectionNegotiationUseCase
- Multiple items across all severities processed in correct order
