---
id: nid_00027pdr09egw4v9btb4vl6z7_E
title: "Implement WorkingTreeValidator — startup guard for clean git state"
status: open
deps: []
links: []
created_iso: 2026-03-18T23:43:56Z
status_updated_iso: 2026-03-18T23:43:56Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [git, startup, validation]
---

Implement WorkingTreeValidator that checks git working tree is clean before any git operations.

## Spec Reference
- doc/core/git.md lines 10-37 (ref.ap.QL051Wl21jmmYqTQTLglf.E)

## Requirements
- Run `git status --porcelain` and verify empty output
- If dirty: fail hard with clear error listing dirty files
- Error message must instruct user to commit or stash before running `shepherd run`
- Include list of dirty files in the error message

## Interface Design
- Create `WorkingTreeValidator` interface + `WorkingTreeValidatorImpl` in `com.glassthought.shepherd.core.supporting.git`
- Single method: `suspend fun validate()` — throws on dirty tree
- Uses `ProcessRunner` to execute `git status --porcelain`
- Optional `workingDir` parameter (consistent with GitBranchManager pattern)

## Error Message Format
```
ERROR: Working tree is not clean. Shepherd requires a clean working tree to avoid
mixing pre-existing uncommitted work with agent output.

Dirty files:
  M  src/Main.kt
  ?? newfile.txt

Please commit or stash your changes before running 'shepherd run'.\n```\n\n## Owner\n- Called by TicketShepherdCreator (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) before branch creation\n\n## Testing\n- Unit tests with fake ProcessRunner (clean tree, dirty tree with various statuses)\n- Integration test in a real git repo (create dirty files, verify failure)\n\n## Existing Code Context\n- GitBranchManager at app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt — follow same pattern (interface+impl, ProcessRunner, optional workingDir)\n- Constants at app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt

