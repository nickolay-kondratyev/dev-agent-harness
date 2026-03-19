# Private Context: FailedToExecutePlanUseCase Implementation

## Status: COMPLETE

## Key Decisions
- `AllSessionsKiller` interface in `usecase.healthmonitoring` package, `TmuxAllSessionsKiller` impl in `core.agent.tmux` package
- `ProcessExiter` in `core.infra` package alongside `DispatcherProvider`
- Tests use `logCheckOverrideAllow(LogLevel.WARN)` for tests that verify WARN-producing behavior
- Tests use `AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true)` to isolate WARN log lines between tests
- `captureStdout` is done inside `it` blocks (suspend context) using a helper function `executeAndCapture`
- Shell environment requires `BASH_ENV="" bash --norc --noprofile` to avoid broken pre_consumption.sh script

## Files Created
- `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ProcessExiter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AllSessionsKiller.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxAllSessionsKiller.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCase.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCase.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt`
