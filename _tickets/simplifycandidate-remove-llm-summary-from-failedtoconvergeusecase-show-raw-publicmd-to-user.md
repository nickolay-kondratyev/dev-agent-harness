---
closed_iso: 2026-03-17T23:30:39Z
id: nid_vjwbfu6yug70h62ao9skd36mr_E
title: "SIMPLIFY_CANDIDATE: Remove LLM summary from FailedToConvergeUseCase — show raw PUBLIC.md to user"
status: closed
deps: []
links: []
created_iso: 2026-03-17T23:13:44Z
status_updated_iso: 2026-03-17T23:30:39Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

In doc/use-case/HealthMonitoring.md (FailedToConvergeUseCase Detail, line 219-225), when the reviewer exceeds iteration.max, the harness uses BudgetHigh DirectLLM (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) to summarize the current state before presenting it to the user.

**Problem:** The LLM summary step adds complexity and fragility:
1. Requires a DirectLLM dependency in the convergence decision path
2. LLM call can fail (network error, rate limit, API error) — adding a failure mode to an already-failing workflow
3. The summary may be misleading — the LLM might miss or misrepresent important details from the PUBLIC.md files
4. Adds token cost for every convergence failure

**Proposed simplification:** Present the raw reviewer PUBLIC.md + doer PUBLIC.md directly to the user. No LLM summarization.

**Why this works just as well or better:**
- The user is an experienced engineer (per CLAUDE.md). They can read two PUBLIC.md files.
- PUBLIC.md files already follow a structured format (## Verdict, ## Issues, ## Acceptance Criteria) — they are designed to be human-readable
- Raw data gives the user complete, unfiltered information to make the continue/abort decision
- If the user wants a summary, they can ask for one separately — the harness should not gate the decision on LLM availability

**Robustness improvement:**
- Removes LLM failure as a blocker in the convergence decision path
- Zero additional cost (no API call)
- User sees actual data, not a potentially lossy summary
- Removes DirectLLM as a dependency of PartExecutor/FailedToConvergeUseCase — simpler dependency graph

Affected specs:
- doc/use-case/HealthMonitoring.md (FailedToConvergeUseCase Detail)
- doc/core/PartExecutor.md (step 4, iteration budget exceeded)


## Notes

**2026-03-17T23:30:34Z**

RESOLVED: Removed LLM summarization from FailedToConvergeUseCase in two spec files:
- doc/use-case/HealthMonitoring.md: UseCase table + Detail section now specify raw PUBLIC.md presentation
- doc/core/DirectLLM.md: Removed FailedToConvergeUseCase as DirectBudgetHighLLM consumer, updated to LlmUserQuestionHandler
Commit: 49fa718
