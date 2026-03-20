---
closed_iso: 2026-03-20T01:26:58Z
id: nid_ao50d028h0kp9vypejprl6iq7_E
title: "Extract shared git staging/commit helper to DRY up FinalCommitUseCaseImpl and CommitPerSubPart"
status: closed
deps: []
links: []
created_iso: 2026-03-20T01:06:25Z
status_updated_iso: 2026-03-20T01:26:58Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
---

FinalCommitUseCaseImpl contains three private methods (stageAll, hasStagedChanges, commit) that are near-identical copies of CommitPerSubPart methods in app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt (lines 133-190).

The only differences are:
- No author attribution in the commit command
- A static FAILURE_CONTEXT instead of one derived from SubPartDoneContext

Suggested fix: Extract the shared git staging/diff/commit logic into a reusable helper (e.g., GitCommitHelper) that both CommitPerSubPart and FinalCommitUseCaseImpl delegate to. This would centralize the processRunner interaction pattern and the hasStagedChanges "exception-means-changes-exist" convention in one place.

Files:
- app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt (lines 45-94)
- app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt (lines 133-190)

