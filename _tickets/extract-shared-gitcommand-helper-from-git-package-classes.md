---
id: nid_j32uihvbfy7bm4miaoee6a4iy_E
title: "Extract shared gitCommand helper from git package classes"
status: open
deps: []
links: []
created_iso: 2026-03-19T17:53:31Z
status_updated_iso: 2026-03-19T17:53:31Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [git, DRY, refactor]
---

GitBranchManagerImpl and CommitPerSubPart both have identical private gitCommand() helper that builds git commands with optional -C workingDir prefix. Extract to a shared utility when a third consumer appears.

Files:
- app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt line 92
- app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt line 191

