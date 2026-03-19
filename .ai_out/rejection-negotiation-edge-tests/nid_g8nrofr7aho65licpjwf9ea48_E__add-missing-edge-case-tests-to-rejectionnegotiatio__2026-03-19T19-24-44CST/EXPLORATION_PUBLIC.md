# Exploration: Rejection Negotiation Edge Case Tests

## Task
Add 5 missing edge-case tests (Tests 9-13) to `RejectionNegotiationUseCaseImplTest.kt`.

## Key Files
- **Test file**: `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt`
- **Production code**: `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt`
- **Parser**: `app/src/main/kotlin/com/glassthought/shepherd/feedback/FeedbackResolutionParser.kt`

## Existing Test Patterns
- BDD with Kotest DescribeSpec, extends AsgardDescribeSpec
- FakeReInstructAndAwait dispatches by handle identity
- buildFileReader helper returns different content on successive reads
- buildHandle helper creates SpawnedAgentHandle stubs
- One assert per `it` block

## Uncovered Production Branches
1. **Lines 131-137**: Reviewer sends Done(COMPLETED) → AgentCrashed("unexpected COMPLETED signal")
2. **Lines 175-179**: FeedbackResolution.SKIPPED after insistence → AgentCrashed("SKIPPED")
3. **Lines 187-191**: ParseResult.InvalidMarker → AgentCrashed("invalid resolution marker")
4. Call count verification on FakeReInstructAndAwait
5. feedbackFilePath forwarding to feedbackFileReader

## FeedbackFileReader Interface
```kotlin
fun interface FeedbackFileReader {
    suspend fun readContent(path: Path): String
}
```
The current `buildFileReader` uses `_ ->` ignoring the path parameter.
