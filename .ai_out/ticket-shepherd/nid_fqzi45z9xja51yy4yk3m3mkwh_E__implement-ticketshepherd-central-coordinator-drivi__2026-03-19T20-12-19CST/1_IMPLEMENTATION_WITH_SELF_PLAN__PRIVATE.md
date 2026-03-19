# Implementation Private: TicketShepherd

## Status: COMPLETED

All tasks done, all tests green.

## Follow-up Items

- `PartExecutorFactory` has only an interface — the real implementation needs to be wired in `TicketShepherdCreator`. This is a separate ticket concern.
- `FinalCommitUseCase` and `TicketStatusUpdater` need real implementations.
- `TicketShepherdCreator` needs to be updated to wire the full `TicketShepherd` (currently only wires `InterruptHandler`).

## Notes

- Kotlin does not allow two private top-level classes with the same name in the same package across different files. Test fakes prefixed with `Ts` to avoid redeclaration error with `TicketShepherdCreatorTest.kt`.
- `ConsoleOutput` changed from `fun interface` to `interface` since it now has 2 methods.
