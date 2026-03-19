---
id: nid_gclq0es4q9fxjekl3czp9xtyw_E
title: "Wire InterruptHandler into TicketShepherdCreator startup"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T17:55:49Z
status_updated_iso: 2026-03-19T18:03:37Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, ticket-shepherd, interrupt, wiring]
---

InterruptHandlerImpl (ref.ap.yWFAwVrZdx1UTDqDJmDpe.E) needs to be wired into TicketShepherdCreator (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) and install() called during startup.

Dependencies to inject:
- SystemClock
- TmuxAllSessionsKiller
- CurrentState
- CurrentStatePersistence
- DefaultConsoleOutput
- DefaultProcessExiter

The install() call should happen after CurrentState is initialized but before the main execution loop starts.

