---
id: nid_yuja09a6v2l54s6bquvskc57p_E
title: "Consolidate duplicate UserQuestionContext data classes"
status: open
deps: []
links: []
created_iso: 2026-03-19T18:19:29Z
status_updated_iso: 2026-03-19T18:19:29Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, user-question, dry]
---

Two identical UserQuestionContext data classes exist:
- app/src/main/kotlin/com/glassthought/shepherd/core/question/UserQuestionContext.kt
- app/src/main/kotlin/com/glassthought/shepherd/core/session/UserQuestionContext.kt

Both have identical fields: question, partName, subPartName, subPartRole, handshakeGuid.

QaDrainAndDeliverUseCase has a manual field-by-field mapping (toQuestionContext companion fun)
that bridges the two. This is a DRY violation and maintenance trap.

Consolidate into a single class and remove the mapping function.
Option 1: Delete session package version, have PendingQuestion use question package version.
Option 2: Delete question package version, have UserQuestionHandler use session package version.

