# RejectionNegotiationUseCase — Implementation State

## Status: COMPLETE

## What Was Done
- Created `RejectionNegotiationUseCase` fun interface + `RejectionNegotiationUseCaseImpl`
- Created `FeedbackFileReader` fun interface for testability
- Created `RejectionResult` sealed class with 4 variants
- 22 unit tests, all passing

## Anchor Points
- ap.cGkhniuHpDBfYmBQH36ea.E — RejectionNegotiationUseCaseImpl

## Integration Notes
- `RejectionNegotiationUseCaseImpl` needs to be wired in `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
- `FeedbackFileReader` needs a real implementation using `kotlin.io.path.readText` + `DispatcherProvider.io()`
- The use case will be called from `PartExecutorImpl` when a doer writes `## Resolution: REJECTED`

## Files
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/FeedbackFileReader.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt`
