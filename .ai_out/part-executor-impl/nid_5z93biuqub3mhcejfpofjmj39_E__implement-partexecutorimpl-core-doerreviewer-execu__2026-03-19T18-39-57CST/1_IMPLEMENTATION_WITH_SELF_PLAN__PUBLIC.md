# PUBLIC: PartExecutorImpl Implementation

## Summary

Implemented `PartExecutorImpl` — the core doer/reviewer execution loop for the TICKET_SHEPHERD harness. This is the central orchestration component that coordinates agent sub-parts (doer and optional reviewer) through the spawn -> instruct -> await -> validate -> commit cycle.

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/.../core/executor/PartExecutor.kt` | `fun interface PartExecutor` with `suspend fun execute(): PartResult` |
| `app/src/main/kotlin/.../core/executor/SubPartConfig.kt` | Static configuration for a sub-part — holds everything needed to spawn, instruct, commit, and validate |
| `app/src/main/kotlin/.../core/executor/PublicMdValidator.kt` | Validates PUBLIC.md exists and is non-empty after Done signal (returns result, not throws) |
| `app/src/main/kotlin/.../core/executor/PartExecutorImpl.kt` | Core implementation with `PartExecutorDeps` dependency bundle |
| `app/src/test/kotlin/.../core/executor/PartExecutorImplTest.kt` | 20 test cases using FakeAgentFacade |

## Key Design Decisions

1. **PartExecutorDeps**: Groups 6 collaborator dependencies into a data class to stay within detekt's parameter-count threshold while preserving constructor injection.

2. **SubPartConfig**: Minimal data class holding static configuration per sub-part. Provides `toSpawnAgentConfig()` for spawning. Iteration state lives in PartExecutorImpl, not the config.

3. **PublicMdValidator**: Separate class with `ValidationResult` sealed return type. Caller maps `Invalid` to `PartResult.AgentCrashed`. Uses `when` expression to satisfy detekt's ReturnCount rule.

4. **Granular feedback loop excluded**: Per ticket scope, NEEDS_ITERATION simply increments iteration and re-instructs doer with reviewer's full PUBLIC.md. Inner per-item feedback loop is a separate ticket.

5. **SelfCompacted**: Throws `error()` since it should be handled inside AgentFacade, never reaching the executor.

## Test Scenarios Covered

- Doer-only: Done(COMPLETED) -> PartResult.Completed
- Doer-only: FailWorkflow -> PartResult.FailedWorkflow
- Doer-only: Crashed -> PartResult.AgentCrashed
- Doer-only: Done(PASS) -> IllegalStateException
- Doer-only: Done(NEEDS_ITERATION) -> IllegalStateException
- Doer-only: Missing PUBLIC.md -> PartResult.AgentCrashed
- Doer-only: Empty PUBLIC.md -> PartResult.AgentCrashed
- Doer-only: Context window read at done boundary
- Doer-only: Session killed on completion
- Doer-only: Spawn config recorded correctly
- Doer-only: Git commit called
- Doer+Reviewer: Happy path (COMPLETED -> PASS) -> PartResult.Completed
- Doer+Reviewer: Both sessions killed
- Doer+Reviewer: Iteration (NEEDS_ITERATION -> re-instruct -> PASS)
- Doer+Reviewer: Git commit per Done signal (4 commits for 2 iterations)
- Doer+Reviewer: Budget exceeded -> PartResult.FailedToConverge
- Doer+Reviewer: Budget exceeded, operator grants more -> PartResult.Completed
- Doer+Reviewer: Doer FailWorkflow
- Doer+Reviewer: Reviewer Crashed
- Doer+Reviewer: Missing reviewer PUBLIC.md after PASS
- Doer+Reviewer: Missing doer PUBLIC.md after COMPLETED
- Doer+Reviewer: Context window read at each done boundary (4 calls for 2 iterations)
- Doer+Reviewer: sendPayload called correct number of times

## Test Results

All 20+ test scenarios pass. Full test suite (`./test.sh`) passes with 0 failures.

## Open Items

- **Granular feedback loop**: Separate ticket. Currently NEEDS_ITERATION just re-instructs doer with reviewer's full PUBLIC.md without processing individual feedback items.
- **Context window self-compaction**: readContextWindowState is called at done boundaries but no compaction action is taken yet (V1 logs only).
