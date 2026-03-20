# Exploration: Wire TicketStatusUpdater

## Key Files
- Interface: `app/src/main/kotlin/com/glassthought/shepherd/usecase/ticketstatus/TicketStatusUpdater.kt`
  - `fun interface TicketStatusUpdater { suspend fun markDone() }`
- Current stub: `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` lines 141-143
- TicketData: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/TicketData.kt` — has `id: String`
- Test fake: `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt` — `TsTicketStatusUpdater`
- Caller: `TicketShepherd.run()` line 109: `deps.ticketStatusUpdater.markDone()`
- TicketShepherdDeps has both `ticketStatusUpdater` and `ticketId: String`

## Architecture Observations
1. `ticketStatusUpdater` is a constructor param of `TicketShepherdCreatorImpl` with TODO default
2. `ticketId` is only known at `create()` time (from `ticketData.id`)
3. **Pattern to follow**: `AllSessionsKillerFactory` — factory in constructor, creates real impl inside `wireTicketShepherd`
4. `ProcessRunner` not in creator — but we can use Java `ProcessBuilder` or add ProcessRunner
5. `tk close <id>` CLI command sets status to "closed" — standard ticket lifecycle status

## Design Decision
- Use `tk close <ticketId>` via ProcessRunner (follows codebase CLI execution patterns)
- Factory pattern: `TicketStatusUpdaterFactory` takes ticketId → creates `TicketStatusUpdaterImpl`
- ProcessRunner needed — either inject into creator or create in factory
