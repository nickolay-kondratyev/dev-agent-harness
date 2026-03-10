---
closed_iso: 2026-03-10T00:00:22Z
id: nid_e6xmtbw1d539id72io3voxnxe_E
title: "Remove duplicate session-exists test in TmuxSessionManagerTest"
status: closed
deps: []
links: []
created_iso: 2026-03-07T22:30:54Z
status_updated_iso: 2026-03-10T00:00:22Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

In app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt, the tests GIVEN TmuxSessionManager WHEN createSession with bash THEN session exists AND GIVEN TmuxSessionManager WHEN sessionExists with existing session THEN returns true both do the same thing: create a session and assert sessionExists == true. The second test is redundant. Remove the duplicate while keeping the more descriptively named test.


## Notes

**2026-03-10T00:00:16Z**

Resolution: The duplicate tests described in this ticket do not exist in the codebase.

Investigated TmuxSessionManagerIntegTest.kt (note: the ticket references "TmuxSessionManagerTest" but the actual file is "TmuxSessionManagerIntegTest.kt").

Current tests are:
1. WHEN session is created -> THEN exists() returns true
2. WHEN killSession is called on existing session -> THEN exists() returns false

These are distinct tests covering different behaviors. The test names mentioned in the ticket (WHEN createSession with bash THEN session exists / WHEN sessionExists with existing session THEN returns true) do not appear anywhere in the git history. The issue was already resolved or never existed.
