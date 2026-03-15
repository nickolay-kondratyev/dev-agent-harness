---
id: nid_mjcvyadvwtzs8noa6iyqq9edm_E
title: "SIMPLIFY_CANDIDATE: Replace AutoRecoveryByAgentUseCase with retry+fail-hard for git ops"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:08:16Z
status_updated_iso: 2026-03-15T01:08:16Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, git, recovery]
---

The AutoRecoveryByAgentUseCase (doc/use-case/AutoRecoveryByAgentUseCase.md) spawns a PI agent via NonInteractiveAgentRunner to fix infrastructure failures like git commit errors. This creates a multi-step chain:
  GitOperationFailureUseCase → AutoRecoveryByAgentUseCase → PI agent subprocess → retry once → FailedToExecutePlanUseCase

Inside a Docker container, git failures are almost always deterministic:
- index.lock file left behind (rm it)
- Disk full (fail hard, nothing to fix)
- Merge conflicts (shouldn't happen — harness controls branching)

Spawning a full PI agent with a 20-minute timeout to fix these is over-engineered.

Proposal: Replace with simple programmatic recovery:
1. Try git operation
2. On failure: check for index.lock → delete if present → retry once
3. On second failure: fail hard with clear error message

This is MORE robust (deterministic, no 20-min blind timeout) and SIMPLER (no subprocess, no agent prompt engineering, no NonInteractiveAgentRunner dependency for this use case).

Note: AutoRecoveryByAgentUseCase may still be useful for OTHER recovery scenarios discovered later. This ticket is specifically about removing it from the git failure path.

Files affected:
- doc/core/git.md (simplify failure handling section)
- doc/use-case/AutoRecoveryByAgentUseCase.md (narrow scope or defer)
- GitOperationFailureUseCase implementation (ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E)

