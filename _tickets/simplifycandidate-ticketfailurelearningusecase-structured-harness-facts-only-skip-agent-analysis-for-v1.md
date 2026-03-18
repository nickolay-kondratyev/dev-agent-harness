---
id: nid_qm9uly3pe6fyw5zhft1rr529q_E
title: "SIMPLIFY_CANDIDATE: TicketFailureLearningUseCase — structured harness facts only, skip agent analysis for V1"
status: open
deps: []
links: []
created_iso: 2026-03-18T02:09:51Z
status_updated_iso: 2026-03-18T02:09:51Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, 80-20, V1-scope]
---

FEEDBACK:
--------------------------------------------------------------------------------
ref.ap.cI3odkAZACqDst82HtxKa.E (TicketFailureLearningUseCase spec)

Currently, spawns a ClaudeCode agent in --print mode (10-min timeout) to analyze .ai_out/ artifacts and produce a failure summary. Agent failure is handled as best-effort fallback to structured facts.

Proposal: For V1, skip the agent entirely and record only the structured facts the harness already knows: failure type, which part failed, last reviewer feedback, agent signals, branch name. These structured facts are already the fallback path.

80/20: The structured facts provide 80% of the value (what failed, where, last feedback). The agent qualitative analysis ("why the approach was wrong") is the remaining 20% and can be added in V2.

Why simpler: Eliminate subprocess spawn, stdout parsing, 10-min timeout handling, agent failure handling.
Why more robust: No external process failure modes. Instant execution. Always succeeds.

File: doc/use-case/TicketFailureLearningUseCase.md
--------------------------------------------------------------------------------

DECISION: Lets keep the --print analysis as it should be reasonably straighforward to do this analysis lets increase the time out to 20 minutes though. 