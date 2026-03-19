# Implementation Private Notes

## Current State: COMPLETE

All required tests pass. `./gradlew :app:test` exits with 0.

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`

## Decisions Made
- Old `TicketShepherdCreator` in `com.glassthought.shepherd.core` marked as SUPERSEDED but kept for backward compat
- AgentFacadeImpl construction deferred — not consumed by any dep in current scope
- Used `NoOpTicketFailureLearningUseCase` as default

## Follow-up Items
- Wire AgentFacadeImpl + ContextForAgentProvider internally when PartExecutorFactory production wiring is implemented
- Wire FinalCommitUseCaseImpl, TicketStatusUpdaterImpl when available
- Remove old `TicketShepherdCreator.kt` + `TicketShepherdCreatorTest.kt` in `core` package once callers migrate
