---
id: nid_fwf09ycnd4d8wdoqd1atuohgb_E
title: "Implement GitCommitStrategy interface + CommitPerSubPart — harness-owned git commits"
status: open
deps: [nid_a93jjhotbiv75jqm9ts3ne3e0_E, nid_m3cm8xizw5qhu1cu3454rca79_E, nid_i1jbftljythkars6fhhc6idne_E]
links: []
created_iso: 2026-03-18T23:45:04Z
status_updated_iso: 2026-03-18T23:45:04Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [git, commit, strategy]
---

Implement GitCommitStrategy interface and CommitPerSubPart V1 implementation.

## Spec Reference
- doc/core/git.md lines 106-133 (GitCommitStrategy section)
- doc/core/git.md lines 96-103 (What Gets Committed)

## GitCommitStrategy Interface
Single-method interface with one hook:
```kotlin
interface GitCommitStrategy {
    suspend fun onSubPartDone(context: SubPartDoneContext)
}
```

### Hook Context (SubPartDoneContext)
Define `SubPartDoneContext` data class in the same file as the `GitCommitStrategy` interface (git package).

The hook receives sufficient context to build commit message and author:
- Part name, sub-part name
- Sub-part role (doer/reviewer)
- Result value (completed, pass, needs_iteration)
- Whether the part has a reviewer (determines if iteration info is included in commit message)
- Current iteration number and max
- Agent type and model (from session record in CurrentState)

## CommitPerSubPart Implementation
V1 default strategy:
1. `git add -A` — stage entire working tree
2. Check `git diff --cached --quiet` — exit code 0 means nothing to commit → skip; non-zero means changes exist → proceed
3. Build commit message via CommitMessageBuilder
4. Build author string via CommitAuthorBuilder
5. `git commit --author="{author} <{email}>" -m "{message}"`
6. On failure → delegate to GitOperationFailureUseCase (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E)

### Empty Commit Handling
- If `git diff --cached` is empty after `git add -A`, skip the commit
- Avoids noise from needs_iteration where reviewer flagged issues but doer hasn't changed code yet\n\n## Location\n- Interface: `com.glassthought.shepherd.core.supporting.git.GitCommitStrategy`\n- Implementation: `com.glassthought.shepherd.core.supporting.git.CommitPerSubPart`\n- Uses ProcessRunner for git commands (consistent with GitBranchManager pattern)\n\n## Dependencies\n- CommitAuthorBuilder + CommitMessageBuilder (ticket nid_a93jjhotbiv75jqm9ts3ne3e0_E)\n- GitOperationFailureUseCase (will be created separately)\n- Part/SubPart data classes (ticket nid_m3cm8xizw5qhu1cu3454rca79_E)\n\n## Testing\n- Unit tests with fake ProcessRunner:\n  - Happy path: changes exist → commit created with correct message and author\n  - Empty diff → commit skipped\n  - Git failure → delegates to GitOperationFailureUseCase\n- Verify correct git commands are called in correct order\n\n## Existing Code Context\n- GitBranchManager at app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt — follow same pattern\n- FailedToExecutePlanUseCase ticket: nid_foubbnsh3vmk1fk34zm75zkg0_E

