# Exploration Summary — TicketFailureLearningUseCase

## Key Files
- **Spec**: `doc/use-case/TicketFailureLearningUseCase.md` (ref.ap.cI3odkAZACqDst82HtxKa.E)
- **Current stub**: `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/TicketFailureLearningUseCase.kt`
- **Caller**: `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCase.kt`
- **Caller test**: `app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCaseImplTest.kt`
- **NonInteractiveAgentRunner**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunner.kt`
- **NonInteractiveAgentRunnerImpl**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/noninteractive/NonInteractiveAgentRunnerImpl.kt`
- **PartResult**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/PartResult.kt`
- **AgentType**: `app/src/main/kotlin/com/glassthought/shepherd/core/data/AgentType.kt`

## Interface Decision
- **Current interface**: `suspend fun recordFailureLearning(partResult: PartResult)` — kept to avoid cascading caller changes
- **Run context** (ticketPath, tryNumber, branchName, originatingBranch, aiOutDir) injected as constructor deps
- **Internal data classes** (`FailureLearningRequest`, `PartResultFailureContext`) used for instruction assembly

## Existing Patterns
- Constructor injection, `outFactory.getOutForClass()`
- `fun interface` for single-method interfaces
- `NonInteractiveAgentResult` sealed class: `Success`, `Failed`, `TimedOut`
- BDD tests with `AsgardDescribeSpec`, GIVEN/WHEN/THEN structure
- Git ops via `ProcessRunner` (process-based)
- `Val(value, ValType.X)` for structured logging
