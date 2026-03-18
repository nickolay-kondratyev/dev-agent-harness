---
closed_iso: 2026-03-18T13:16:57Z
id: nid_hgi8ohge4qlpn28g0iyoqrk5b_E
title: "SIMPLIFY_CANDIDATE: Replace Ctrl+C 10-second stdin confirmation with double-Ctrl+C timestamp pattern"
status: closed
deps: []
links: []
created_iso: 2026-03-18T02:09:58Z
status_updated_iso: 2026-03-18T13:16:57Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, UX, stdin]
---

ref.ap.P3po8Obvcjw4IXsSUSU91.E (TicketShepherd spec)

Currently on Ctrl+C, the harness blocks on stdin for 10 seconds asking the user to confirm termination. This is non-standard CLI UX and has a subtle interaction: it uses stdin, which is also used by StdinUserQuestionHandler for Q&A.

Proposal: Use the standard double-Ctrl+C pattern — track the timestamp of the last SIGINT. If a second SIGINT arrives within 2 seconds, terminate. Otherwise print "Press Ctrl+C again to confirm" and reset.

Why simpler: Timestamp comparison (2 lines) vs. stdin reading with timeout (blocking I/O, thread management).
Why more robust: No stdin contention with Q&A handler. No 10-second blocking window. Standard CLI idiom that users already know.

File: doc/core/TicketShepherd.md


## Notes

**2026-03-18T13:16:54Z**

Spec updated in doc/core/TicketShepherd.md. Replaced the 10-second stdin confirmation flow with the double-Ctrl+C timestamp pattern: first Ctrl+C prints a reminder and records the timestamp; second Ctrl+C within 2 seconds triggers termination; after 2 seconds the counter resets. Added rationale in the spec: no stdin contention, no blocking window, standard CLI idiom.
