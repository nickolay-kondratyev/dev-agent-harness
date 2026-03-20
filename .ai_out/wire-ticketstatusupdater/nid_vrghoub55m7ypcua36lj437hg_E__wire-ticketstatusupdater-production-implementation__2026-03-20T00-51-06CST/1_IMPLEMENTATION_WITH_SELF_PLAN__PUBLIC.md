# Implementation: Wire TicketStatusUpdater

## Summary
Wired production `TicketStatusUpdater` using `ticket close <id>` CLI command via ProcessRunner.

## Files Created
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/ticketstatus/TicketStatusUpdaterImpl.kt` — production impl
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/ticketstatus/TicketStatusUpdaterImplTest.kt` — unit tests

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`:
  - Changed `ticketStatusUpdater: TicketStatusUpdater` -> `ticketStatusUpdaterFactory: TicketStatusUpdaterFactory`
  - Added `TicketStatusUpdaterFactory` fun interface (same file, bottom)
  - Default factory creates `TicketStatusUpdaterImpl` with `ProcessRunner.standard(outFactory)`
  - `wireTicketShepherd()` now calls `ticketStatusUpdaterFactory.create(ticketData.id, outFactory)`
  - Updated KDoc (removed "not yet wired" mention for ticketStatusUpdater)
- `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` — added `TICKET_ID` ValType
- `app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt` — updated to use factory

## Design Decisions
1. **Factory pattern** — ticketId only known at `create()` time, so factory defers construction (matches `AllSessionsKillerFactory` pattern)
2. **`ticket` not `tk`** — `tk` is a shell alias; `ticket` is the actual executable guaranteed on PATH
3. **ProcessRunner** — follows codebase pattern for subprocess execution (structured, testable)
4. **Structured logging** — uses `ShepherdValType.TICKET_ID` for both log statements

## Test Results
- All 81 test classes pass with 0 failures (verified via XML test results)
- New test: `TicketStatusUpdaterImplTest` — 2 test cases verifying correct CLI command invocation
- Build reports XML file writing errors — pre-existing infrastructure issue unrelated to these changes
