---
closed_iso: 2026-03-19T17:20:07Z
id: nid_i1jbftljythkars6fhhc6idne_E
title: "Implement GitOperationFailureUseCase — index.lock fast-path + fail-fast"
status: closed
deps: [nid_foubbnsh3vmk1fk34zm75zkg0_E]
links: []
created_iso: 2026-03-18T23:45:37Z
status_updated_iso: 2026-03-19T17:20:07Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [git, error-handling]
---

Implement GitOperationFailureUseCase for handling git operation failures during workflow.

## Spec Reference
- doc/core/git.md lines 212-273 (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E)

## V1 Approach: index.lock fast-path + fail-fast

### Fast-path: index.lock detection
Before failing, check for the most common recoverable case:
1. Error output contains `index.lock` OR `unable to lock`
2. `.git/index.lock` file exists

If BOTH conditions are true:
1. Delete `.git/index.lock`
2. Retry the original git operation immediately
3. If retry succeeds → continue workflow normally
4. If retry fails → fall through to fail-fast

**Why-not just delete blindly**: Only delete when the error confirms a lock problem — avoids masking unrelated failures.

### Fail-fast for all other git failures
If fast-path not applicable or retry fails:
1. Log the git command that failed, error output (stderr), and current `git status`
2. Delegate to FailedToExecutePlanUseCase — prints red error, kills all sessions, exits non-zero

Error message includes:
- The git command that failed
- The stderr output
- Current workflow position (part name, sub-part, iteration)

### Ownership
- Failure handling is encapsulated within GitCommitStrategy implementation
- Executor calls onSubPartDone and either gets success or FailedToExecutePlanUseCase escalation
- Executor does NOT orchestrate any recovery itself

### Failure Points Covered
| Git Operation | Where It Happens | Recovery Via |
|---|---|---|
| git add -A | GitCommitStrategy.onSubPartDone | index.lock fast-path → fail-fast |
| git commit | GitCommitStrategy.onSubPartDone | index.lock fast-path → fail-fast |
| git checkout -b | TicketShepherdCreator (startup) | NOT covered — startup failures fail hard |

## V2 Note
- AutoRecoveryByAgentUseCase (ref.ap.q54vAxzZnmWHuumhIQQWt.E) deferred to V2
- Agent-based recovery adds complexity and delay; fail-fast is more robust for V1

## Location
- `com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase`
- Consistent with the rest of the git code in `core.supporting.git` package

## Dependencies
- FailedToExecutePlanUseCase (ticket nid_foubbnsh3vmk1fk34zm75zkg0_E)
- NOTE: This use case is called FROM CommitPerSubPart (ticket nid_fwf09ycnd4d8wdoqd1atuohgb_E), but has NO build dependency on it. It should be implemented before or in parallel with CommitPerSubPart.

## Testing
- Unit tests with fake filesystem and fake ProcessRunner:
  - index.lock error + file exists → delete + retry succeeds
  - index.lock error + file exists → delete + retry fails → fail-fast
  - index.lock error but file doesn't exist → fail-fast
  - Non-lock-related error → fail-fast immediately
  - Verify FailedToExecutePlanUseCase is called with correct context (including part name, sub-part, iteration)

## Existing Code Context
- FailedToExecutePlanUseCase ticket: nid_foubbnsh3vmk1fk34zm75zkg0_E
- GitBranchManager at app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt — follow similar pattern

