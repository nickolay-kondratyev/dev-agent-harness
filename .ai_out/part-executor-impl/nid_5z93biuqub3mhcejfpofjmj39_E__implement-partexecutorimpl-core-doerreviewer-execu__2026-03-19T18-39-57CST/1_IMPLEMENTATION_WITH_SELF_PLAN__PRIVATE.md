# PRIVATE: PartExecutorImpl Implementation

## Status: COMPLETE (post-review iteration)

## Plan

**Goal**: Implement PartExecutorImpl core doer/reviewer execution loop with FakeAgentFacade tests.

**Steps**:
1. [x] Create SubPartConfig data class
2. [x] Create PartExecutor interface
3. [x] Create PublicMdValidator
4. [x] Create PartExecutorImpl
5. [x] Create PartExecutorImplTest with comprehensive test scenarios
6. [x] Fix detekt issues (TooManyFunctions, MaxLineLength, ReturnCount, UseCheckOrError, LongParameterList)
7. [x] Run full test suite — all tests pass
8. [x] Review iteration: lazy reviewer spawn (I1)
9. [x] Review iteration: transitionTo comment (I2)
10. [x] Review iteration: specific ValTypes (I4)
11. [x] Review iteration: reviewer Done(COMPLETED) test (S3)
12. [x] Review iteration: lazy spawn verification test
13. [x] All tests pass after review iteration

## Key Decisions Made

1. **PartExecutorDeps bundle**: Introduced to group 6 collaborator dependencies into a single parameter, staying within detekt's 8-parameter threshold for constructors.

2. **@Suppress("TooManyFunctions")**: Applied to PartExecutorImpl since it's a legitimate orchestration class with 14 private methods — each well-decomposed with clear responsibility.

3. **PublicMdValidator as separate class**: Returns ValidationResult (not throws) so caller can map to PartResult.AgentCrashed cleanly.

4. **terminateWith helper**: Consolidates session cleanup and status update into a single method.

5. **SelfCompacted handling**: Throws error() since per spec, SelfCompacted is handled inside AgentFacade.

6. **Lazy reviewer spawn (review iteration)**: Reviewer is spawned after doer's first Done(COMPLETED), not eagerly. `reviewerHandle` is nullable. `mapDoerSignalInReviewerPath` accepts nullable reviewer handle.

7. **Specific ValTypes (review iteration)**: Added ITERATION_COUNT, MAX_ITERATIONS, SUB_PART_NAME, CONTEXT_WINDOW_REMAINING to ShepherdValType. Replaced all ValType.STRING_USER_AGNOSTIC in PartExecutorImpl.

## Files Created/Modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutor.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfig.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PublicMdValidator.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` (modified in review iteration)
- `app/src/main/kotlin/com/glassthought/shepherd/core/ShepherdValType.kt` (modified in review iteration)
- `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (modified in review iteration)
