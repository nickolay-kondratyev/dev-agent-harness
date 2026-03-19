# Exploration: GitCommitStrategy + CommitPerSubPart

## Task Summary
Implement `GitCommitStrategy` interface with single hook `onSubPartDone` and V1 `CommitPerSubPart` implementation.

## Spec Reference
- `doc/core/git.md` lines 105-131 — GitCommitStrategy section

## Existing Dependencies (ALL already implemented)

### CommitAuthorBuilder
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/CommitAuthorBuilder.kt`
- **Signature**: `object CommitAuthorBuilder { fun build(agentType: AgentType, model: String, hostUsername: String): String }`
- **Output**: `"CC_sonnet_WITH-nickolaykondratyev"`

### CommitMessageBuilder
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/CommitMessageBuilder.kt`
- **Signature**: `object CommitMessageBuilder { fun build(partName, subPartName, result, hasReviewer, currentIteration, maxIterations): String }`
- **Output**: `"[shepherd] planning/plan — completed"` or `"[shepherd] ui_design/review — needs_iteration (iteration 1/3)"`

### GitOperationFailureUseCase
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCase.kt`
- **Signature**: `fun interface GitOperationFailureUseCase { suspend fun handleGitFailure(gitCommand: List<String>, errorOutput: String, context: GitFailureContext) }`
- `GitFailureContext(partName, subPartName, iterationNumber)`

### AgentType
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/data/AgentType.kt`
- Enum: `CLAUDE_CODE`, `PI`

### SubPart & IterationConfig
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPart.kt`
- `data class SubPart(name, role, agentType, model, status?, iteration?, sessionIds?)`
- `data class IterationConfig(max, current = 0)`

## Pattern to Follow: GitBranchManager
- **Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt`
- Interface + Impl in same file
- Constructor: `outFactory: OutFactory, processRunner: ProcessRunner, workingDir: Path? = null`
- Uses `processRunner.runProcess(*gitCommand("arg1", "arg2"))` pattern
- Private `gitCommand()` helper for `-C <workingDir>` prefixing
- Companion `fun standard(...)` factory

## Existing Tests Pattern
- **Location**: `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/`
- BDD style with `AsgardDescribeSpec`, GIVEN/WHEN/THEN via nested `describe`/`it`
- One assert per `it` block
- `outFactory` inherited from `AsgardDescribeSpec`
- Uses fake ProcessRunner for unit tests

## Implementation Plan (from ticket)
1. `SubPartDoneContext` data class — hook context (same file as interface)
2. `GitCommitStrategy` interface — single `onSubPartDone(context)` method
3. `CommitPerSubPart` implementation:
   - `git add -A`
   - `git diff --cached --quiet` — exit 0 = nothing to commit → skip
   - Build commit message via `CommitMessageBuilder`
   - Build author via `CommitAuthorBuilder`
   - `git commit --author="{author} <{email}>" -m "{message}"`
   - On failure → delegate to `GitOperationFailureUseCase`

## Key Design Notes
- `hostUsername` needed for CommitAuthorBuilder — should be a constructor parameter
- `email` for commit author — spec says `{author}@shepherd` (see doc/core/git.md line ~170)
- ProcessRunner throws on non-zero exit → need try-catch for git commands that may fail
- `git diff --cached --quiet` returns non-zero when there ARE changes (counterintuitive) → need to handle the exception case as "changes exist"
