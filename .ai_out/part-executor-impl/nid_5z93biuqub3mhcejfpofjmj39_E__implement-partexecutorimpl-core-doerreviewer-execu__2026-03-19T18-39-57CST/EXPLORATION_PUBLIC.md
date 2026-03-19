# Exploration: PartExecutorImpl Implementation

## Scope
Implement `PartExecutorImpl` — core doer/reviewer execution loop. EXCLUDES inner granular feedback loop (separate ticket nid_fq8wn0eb9yrvzcpzdurlmsg7i_E).

## Key Existing Types

### Interfaces
- `AgentFacade` — `app/src/main/kotlin/.../core/agent/facade/AgentFacade.kt` — spawnAgent, sendPayloadAndAwaitSignal, readContextWindowState, killSession
- `ContextForAgentProvider` — `app/src/main/kotlin/.../core/context/ContextForAgentProvider.kt` — assembleInstructions(request): Path
- `GitCommitStrategy` — `app/src/main/kotlin/.../core/supporting/git/GitCommitStrategy.kt` — onSubPartDone(context)
- `FailedToConvergeUseCase` — `app/src/main/kotlin/.../usecase/healthmonitoring/FailedToConvergeUseCase.kt` — askForMoreIterations(currentMax, iterationsUsed): Boolean

### Data Types
- `AgentSignal` — sealed: Done(DoneResult), FailWorkflow(reason), Crashed(details), SelfCompacted
- `DoneResult` — enum: COMPLETED, PASS, NEEDS_ITERATION
- `PartResult` — sealed: Completed, FailedWorkflow, FailedToConverge, AgentCrashed
- `SpawnAgentConfig` — partName, subPartName, subPartIndex, agentType, model, role, systemPromptPath, bootstrapMessage
- `AgentPayload` — instructionFilePath: Path
- `SpawnedAgentHandle` — guid, sessionId, lastActivityTimestamp
- `ContextWindowState` — remainingPercentage: Int?
- `SubPartStatus` — enum: NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED
- `SubPartStateTransition` — sealed: Spawn, Complete, Fail, IterateContinue
- `IterationConfig` — max: Int, current: Int
- `SubPart` — name, role, agentType, model, status?, iteration?, sessionIds?
- `SubPartRole` — enum: DOER, REVIEWER (fromIndex)
- `SubPartDoneContext` — for GitCommitStrategy
- `AgentInstructionRequest` — sealed: DoerRequest, ReviewerRequest, PlannerRequest, PlanReviewerRequest

### Test Infrastructure
- `FakeAgentFacade` — `app/src/test/kotlin/.../core/agent/facade/FakeAgentFacade.kt`
- Test pattern: AsgardDescribeSpec, GIVEN/WHEN/THEN, one assert per `it`
- Helper builders: buildHandle(), buildConfig(), buildPayload()

## What Needs Creating
1. `SubPartConfig` data class — wraps spawn config + context needed for instruction assembly
2. `PartExecutor` interface — `suspend fun execute(): PartResult`
3. `PartExecutorImpl` — core implementation
4. `PublicMdValidator` — validates PUBLIC.md exists and non-empty after Done signal
5. Comprehensive unit tests using FakeAgentFacade

## Key Spec Constraints (from doc/core/PartExecutor.md)
- R6: NO Clock, SessionsState, AgentUnresponsiveUseCase, ContextWindowStateReader in PartExecutorImpl constructor
- Both TMUX sessions alive for full part lifecycle (doer+reviewer path)
- Re-instruction pattern: don't respawn, use sendPayloadAndAwaitSignal on existing handle
- PUBLIC.md validation after EVERY AgentSignal.Done
- Git commit after every Done signal (after PUBLIC.md validation)
- Context window check at done boundaries via readContextWindowState
- Status validated via SubPartStateTransition before every mutation
- Done(PASS)/Done(NEEDS_ITERATION) in doer-only path → IllegalStateException
