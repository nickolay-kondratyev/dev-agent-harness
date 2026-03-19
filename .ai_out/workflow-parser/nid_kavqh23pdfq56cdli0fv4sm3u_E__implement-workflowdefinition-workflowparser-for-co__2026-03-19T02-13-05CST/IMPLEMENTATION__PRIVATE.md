# Implementation Private State

## Iteration 2: Review Feedback Addressed

### What was done
1. Added init-block validation in `WorkflowDefinition.kt` rejecting `executionPhasesFrom` for straightforward workflows.
2. Added corresponding test in `WorkflowDefinitionTest.kt`.
3. Moved all `parser.parse()` suspend calls from `describe` block bodies into `it` blocks in `WorkflowParserTest.kt`.
4. Extracted `createTempWorkflowDir()` helper to DRY up temp-dir boilerplate across 3 error-case test blocks.

### Key decision: helper function shape
Initially tried `suspend fun withWorkflowFile(..., block: suspend (Path) -> Unit)` but Kotlin does not allow `suspend` local functions in non-suspend init lambdas. Changed to a simpler `createTempWorkflowDir(): Path` factory with `try/finally` cleanup in each `it` block.

### Files modified
- `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinition.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowDefinitionTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/WorkflowParserTest.kt`

### Test status
All tests pass (`./gradlew :app:test`).
