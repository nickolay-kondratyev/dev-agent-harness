---
id: nid_hzyl1737cj1dn919ndbn0gln3_E
title: "Clean up stale WHY-NOT protocol references in code"
status: open
deps: []
links: []
created_iso: 2026-03-17T19:31:51Z
status_updated_iso: 2026-03-17T19:31:51Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [cleanup, context-assembly]
---

Two code files still reference the removed anchor point ap.kmiKk7vECiNSpJjAXYMyE.E:

1. app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt:65
   - WHY_NOT_COMMENT KDoc references the removed AP
   - Consider removing the WHY_NOT_COMMENT vocabulary entry entirely, or simplifying its doc

2. app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSections.kt:201
   - whyNotReminder section references the removed AP
   - Consider removing the whyNotReminder section and its usage in doer instruction assembly

These became stale when the WHY-NOT Comments Protocol spec section was dropped from V1 scope.
See commit that removed the spec: branch CC_nid_o27zof98wzuwuc5c85x4q3vrb_E__simplifycandidate-drop-why-not-comments-protocol-from-v1-scope_opus-v4.6

