# Exploration: GitOperationFailureUseCase Context

## Spec (doc/core/git.md lines 212-273, ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E)

**V1 Approach:** index.lock fast-path + fail-fast

### Fast-Path: index.lock Detection
Triggers when BOTH conditions are true:
1. Error output contains `index.lock` OR `unable to lock`
2. `.git/index.lock` file exists on disk

Actions:
1. Delete `.git/index.lock`
2. Retry the original git operation immediately
3. If retry succeeds → continue normally
4. If retry fails → fall through to fail-fast

### Fail-Fast
When fast-path not applicable or retry failed:
1. Log git command that failed, stderr, current `git status`
2. Escalate to `FailedToExecutePlanUseCase`

## Key Existing Code

### Git Package: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/`
- `GitBranchManager.kt` — interface + impl, uses ProcessRunner, fail-fast on errors
- `BranchNameBuilder.kt` — stateless utility
- `CommitMessageBuilder.kt` — stateless utility
- `CommitAuthorBuilder.kt` — stateless utility

### FailedToExecutePlanUseCase
**File:** `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToExecutePlanUseCase.kt`

```kotlin
fun interface FailedToExecutePlanUseCase {
    suspend fun handleFailure(failedResult: PartResult): Nothing
}
```

Constructor deps: OutFactory, ConsoleOutput, AllSessionsKiller, TicketFailureLearningUseCase, ProcessExiter

Flow: print RED → kill sessions → record learning (best-effort) → exit(1)

### PartResult (sealed class)
```kotlin
sealed class PartResult {
    object Completed : PartResult()
    data class FailedWorkflow(val reason: String) : PartResult()
    data class FailedToConverge(val summary: String) : PartResult()
    data class AgentCrashed(val details: String) : PartResult()
}
```

### ProcessRunner (asgard)
- `suspend fun runProcess(vararg args: String): String` — returns stdout, throws on non-zero exit
- Factory: `ProcessRunner.standard(outFactory)`

## Patterns to Follow
- Constructor injection only, factory companions
- Structured logging: `Val(value, ValType.X)`, snake_case messages
- Suspend functions throughout
- BDD tests with AsgardDescribeSpec, one assert per `it` block
- Fakes for all external dependencies
