---
id: nid_e6xmtbw1d539id72io3voxnxe_E
title: "Remove duplicate session-exists test in TmuxSessionManagerTest"
status: open
deps: []
links: []
created_iso: 2026-03-07T22:30:54Z
status_updated_iso: 2026-03-07T22:30:54Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

In app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt, the tests GIVEN TmuxSessionManager WHEN createSession with bash THEN session exists AND GIVEN TmuxSessionManager WHEN sessionExists with existing session THEN returns true both do the same thing: create a session and assert sessionExists == true. The second test is redundant. Remove the duplicate while keeping the more descriptively named test.

