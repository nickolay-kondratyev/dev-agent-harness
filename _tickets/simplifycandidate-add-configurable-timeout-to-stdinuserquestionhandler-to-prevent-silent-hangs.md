---
closed_iso: 2026-03-17T22:03:43Z
id: nid_zcx2kqker77ku9xvwsq607tt6_E
title: "SIMPLIFY_CANDIDATE: Add configurable timeout to StdinUserQuestionHandler to prevent silent hangs"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:05:21Z
status_updated_iso: 2026-03-17T22:03:43Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, ux, qa-flow]
---


FEEDBACK:
--------------------------------------------------------------------------------
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

--------------------------------------------------------------------------------

DECISION: NO lets allow the STDIN feedback to wait for user. However long it takes. Justification user could be in a meeting or went on a walk. We should NOT fail workflow if it took even hours to answer the question. The workflow should stop and wait. Lets document this use case in spec.
## Notes

**2026-03-17T22:03:43Z**

RESOLUTION:

Decision: NO timeout added. The StdinUserQuestionHandler intentionally blocks indefinitely.

Rationale documented in spec (doc/core/UserQuestionHandler.md):
- Harness is for semi-attended workflows; user could be in a meeting or on a walk.
- Failing the workflow on inactivity is worse than pausing — user returns to destroyed work.
- Mental model: the workflow stops and waits, as long as needed.

Spec changes made:
- Updated "If human is away" table row to say "Blocks indefinitely. The workflow pauses and resumes when the human returns."
- Updated "Timeout" row to say "None — intentionally blocks indefinitely."
- Added a new "Design Decision: No Timeout on Stdin Q&A" subsection explaining the rationale and warning about unattended environments.
