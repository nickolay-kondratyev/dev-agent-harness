# Implementation Private Notes

## Status: COMPLETE

## What was done
- Wired `SetupPlanUseCaseFactory` default in `TicketShepherdCreatorImpl` to construct real `SetupPlanUseCaseImpl`
- Straightforward workflows now work at runtime
- DetailedPlanningUseCase remains TODO (blocked on PlanningPartExecutorFactory production impl)

## Remaining TODOs in TicketShepherdCreatorImpl
- `DetailedPlanningUseCase` — needs `PlanningPartExecutorFactory` production impl
- `PartExecutorFactory` — needs AgentFacadeImpl + ContextForAgentProvider
- `FinalCommitUseCase` — needs production impl
- `TicketStatusUpdater` — needs production impl
