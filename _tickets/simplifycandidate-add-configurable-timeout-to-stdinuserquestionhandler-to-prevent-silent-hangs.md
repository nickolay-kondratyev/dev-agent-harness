---
id: nid_zcx2kqker77ku9xvwsq607tt6_E
title: "SIMPLIFY_CANDIDATE: Add configurable timeout to StdinUserQuestionHandler to prevent silent hangs"
status: open
deps: []
links: []
created_iso: 2026-03-17T21:05:21Z
status_updated_iso: 2026-03-17T21:05:21Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, ux, qa-flow]
---

Per spec (doc/core/UserQuestionHandler.md), V1 StdinUserQuestionHandler blocks indefinitely waiting for stdin input.

If the harness process is:
- Backgrounded (nohup / disowned)
- TTY detached
- Run in CI without a terminal
- Left unattended after a Q&A prompt

...the entire workflow silently hangs forever. There is no timeout, no warning, no fallback.

**Opportunity:** Add a configurable Q&A timeout (e.g., `--qa-timeout-minutes`, default 30 minutes):
- If stdin has no response within the timeout, the harness logs a clear warning and signals `PartResult.FailedWorkflow("qa_timeout: no response within N minutes")`
- This terminates cleanly rather than hanging silently

This is a tiny addition to `StdinUserQuestionHandler` that makes the harness substantially more robust when run semi-attended or from scripts.

Simpler alternative (if a full timeout is overkill): at minimum print a visible warning every 5 minutes reminding the operator that Q&A is pending.

Spec reference: doc/core/UserQuestionHandler.md

