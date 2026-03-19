# Exploration: FailedToConvergeUseCase

## Key Files
- **HarnessTimeoutConfig**: `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` — needs `failedToConvergeIterationIncrement: Int = 2`
- **PartResult**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/PartResult.kt` — `PartResult.FailedToConverge(summary)` already exists
- **HealthMonitoring package**: `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/`
- **ConsoleOutput**: `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ConsoleOutput.kt` — `fun interface` with `printlnRed`
- **Spec**: `doc/use-case/HealthMonitoring.md` lines 216-234
- **Existing test pattern**: `app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt`

## Patterns
- `fun interface` for single-method abstractions
- Constructor injection, `OutFactory` for logging
- `AsgardDescribeSpec` BDD tests with one assert per `it` block
- FakeConsoleOutput pattern for tests
- Need StdinReadable interface (or similar) for injectable stdin I/O

## Existing HarnessTimeoutConfig fields
startupAckTimeout, healthCheckInterval, noActivityTimeout, pingTimeout, payloadAckTimeout, payloadAckMaxAttempts, selfCompactionTimeout, contextFileStaleTimeout, contextWindowSoftThresholdPct, contextWindowHardThresholdPct

## PartResult (sealed class)
- Completed, FailedWorkflow(reason), FailedToConverge(summary), AgentCrashed(details)
