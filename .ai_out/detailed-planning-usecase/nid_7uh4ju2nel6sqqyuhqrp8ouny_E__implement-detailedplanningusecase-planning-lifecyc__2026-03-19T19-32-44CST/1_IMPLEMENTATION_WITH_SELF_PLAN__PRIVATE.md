# Implementation Private: DetailedPlanningUseCase

## Status: COMPLETE

## Implementation Notes

- The `AsgardDescribeSpec` has a log verification mechanism that fails tests when WARN/ERROR logs are emitted.
  Tests that trigger WARN logs (PlanConversionException retry paths) use
  `.config(extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)))` to allow them.
- Test class uses `AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true)` since
  tests emit log lines via the implementation's `Out` logger.
- Each `it` block creates its own fakes to avoid shared mutable state between tests
  (fakes use queues that get consumed).

## Wiring Note

`DetailedPlanningUseCaseImpl` is not yet wired into `TicketShepherdCreator`. The caller
(`SetupPlanUseCase`) will need to create it with:
- A `PlanningPartExecutorFactory` that creates `PartExecutorImpl` configured with PLANNER/PLAN_REVIEWER
- The shared `PlanFlowConverter`
- The shared `FailedToExecutePlanUseCase`
- The `CurrentState` instance
- A `maxConversionRetries` value (suggest 3 as default)
