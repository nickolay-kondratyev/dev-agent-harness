---
id: nid_ou1fy9kv67zrv8dwnlu6dsf9g_E
title: "Consider removing question field duplication between PendingQuestion and UserQuestionContext"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T17:57:53Z
status_updated_iso: 2026-03-19T18:04:01Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, sessions-state, DRY]
---

PendingQuestion.question duplicates UserQuestionContext.question.
Both specs (SessionsState.md and UserQuestionHandler.md) define the field.
Consider removing PendingQuestion.question and using context.question,
or converting the queue to ConcurrentLinkedQueue<UserQuestionContext>.

Files:
- app/src/main/kotlin/com/glassthought/shepherd/core/session/PendingQuestion.kt
- app/src/main/kotlin/com/glassthought/shepherd/core/session/UserQuestionContext.kt
- doc/core/SessionsState.md
- doc/core/UserQuestionHandler.md

