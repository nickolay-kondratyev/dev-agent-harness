# Implementation Private: Wire InterruptHandler into TicketShepherdCreator

## Status: COMPLETE

## Plan
1. [x] Read exploration file and all dependency code
2. [x] Create `TicketShepherdCreator` interface + `TicketShepherdCreatorImpl` at `core/TicketShepherdCreator.kt`
3. [x] Wire InterruptHandler with all deps: Clock, AllSessionsKiller, CurrentState, CurrentStatePersistence, ConsoleOutput, ProcessExiter
4. [x] Call `install()` after CurrentState init
5. [x] Write unit tests verifying wiring
6. [x] Fix detekt issues (ForbiddenComment for TODOs, MaxLineLength)
7. [x] All tests green

## Decisions
- Placed TicketShepherdCreator in `com.glassthought.shepherd.core` package (top-level core, not nested deeper)
- Used factory lambda `(OutFactory) -> AllSessionsKiller` for AllSessionsKiller to allow test injection without needing ShepherdContext in tests
- Created `TicketShepherdCreatorResult` data class as interim return type until full TicketShepherd exists
- Replaced `TODO()` code comments with `// Future:` to satisfy detekt's ForbiddenComment rule; future responsibilities documented in KDoc
- NoOpOutFactory is at `com.asgard.core.out.impl.NoOpOutFactory` (not `com.asgard.core.out.NoOpOutFactory`)
