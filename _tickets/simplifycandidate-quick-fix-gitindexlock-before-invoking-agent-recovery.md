---
closed_iso: 2026-03-17T22:06:03Z
id: nid_tr3hj37mz5t4hubzap775jj36_E
title: "SIMPLIFY_CANDIDATE: Quick-fix .git/index.lock before invoking agent recovery"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:04:56Z
status_updated_iso: 2026-03-17T22:06:03Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, git, recovery]
---

Current flow in GitOperationFailureUseCase:
  git failure → AutoRecoveryByAgentUseCase → NonInteractiveAgentRunner (PI agent, up to 20 min)

The most common git failure in a CI/agent environment is a stale `.git/index.lock` file left by a crashed process. This is a fully deterministic fix: `rm -f .git/index.lock`.

**Opportunity:** Before escalating to full agent recovery (20 min latency), detect the specific case:
- Error message contains `index.lock` or `unable to lock` 
- The lock file exists

If so, delete it and retry the git operation directly. Only invoke the agent recovery path for genuinely complex or unknown git failures.

This both simplifies the common-case flow (no agent, no 20-minute wait) AND improves robustness (deterministic fix vs. non-deterministic LLM agent).

Spec reference: doc/core/git.md (GitOperationFailureUseCase), doc/use-case/AutoRecoveryByAgentUseCase.md


## Notes

**2026-03-17T22:06:09Z**

Resolved by updating doc/core/git.md spec only. Added 'Fast-path: index.lock detection' sub-section to GitOperationFailureUseCase. Before escalating to AutoRecoveryByAgentUseCase (20min LLM agent), GitOperationFailureUseCase now checks if error contains 'index.lock'/'unable to lock' AND .git/index.lock exists — if so, deletes the lock and retries immediately. Only falls through to agent recovery for other/unresolved failures. Also updated Failure Points table and Git Operations Summary table. Committed as 7ea166c.
