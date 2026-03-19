---
id: nid_7vkcmaxmbzayvjgoaff2ifeur_E
title: "Evaluate whether SessionEntry should be a regular class instead of data class"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T18:26:16Z
status_updated_iso: 2026-03-19T18:45:59Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, technical-debt]
---

SessionEntry has mutable fields: AtomicReference<PayloadId?>, CompletableDeferred<AgentSignal>, ConcurrentLinkedQueue<PendingQuestion>.
The data class auto-generated equals/hashCode/copy use reference equality for these fields, which may be unexpected.
Evaluate whether SessionEntry should be a regular class or if the current behavior is acceptable.

File: app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt
Ref: ref.ap.igClEuLMC0bn7mDrK41jQ.E

