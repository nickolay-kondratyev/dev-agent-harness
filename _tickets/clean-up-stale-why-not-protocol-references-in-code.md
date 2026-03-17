---
closed_iso: 2026-03-17T21:30:07Z
id: nid_hzyl1737cj1dn919ndbn0gln3_E
title: "Clean up stale WHY-NOT protocol references in code"
status: closed
deps: []
links: []
created_iso: 2026-03-17T19:31:51Z
status_updated_iso: 2026-03-17T21:30:07Z
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


## Notes

**2026-03-17T21:30:01Z**

Resolved by removing stale ap.kmiKk7vECiNSpJjAXYMyE.E references:

- ProtocolVocabulary.kt: simplified KDoc for WHY_NOT constant (removed stale AP ref)
- InstructionSections.kt: removed WHY_NOT_REMINDER val entirely (spec was dropped from V1 scope)
- ContextForAgentProviderImpl.kt: removed WHY_NOT_REMINDER from doer instruction assembly
- Fixed dangling "see below" in DOER_PUSHBACK_GUIDANCE (was pointing to removed section)
- Updated two stale test descriptions (no longer reference "WHY-NOT reminder" or "protocol")

WHY_NOT constant remains — it is still used in REVIEWER_FEEDBACK_FORMAT, DOER_PUSHBACK_GUIDANCE,
feedbackItemInstructions, and REJECTED_FEEDBACK_HEADER. All tests pass.
