---
id: nid_5hbndodsljf2i7kku0qcd9kgm_E
title: "Wire per-item feedback content into DoerRequest for ContextForAgentProvider"
status: open
deps: []
links: []
created_iso: 2026-03-19T20:03:45Z
status_updated_iso: 2026-03-19T20:03:45Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [granular-feedback-loop, ContextForAgentProvider, inner-loop]
---

InnerFeedbackLoop.buildFeedbackItemRequest() currently creates a generic DoerRequest without
feedback file content/path. ContextForAgentProvider cannot assemble per-item instructions
for the doer. Need to either:
- Add a FeedbackItemRequest to AgentInstructionRequest sealed class, or
- Extend DoerRequest with optional feedback file path/content fields

This is Gate 2/R4 from doc/plan/granular-feedback-loop.md (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

File: app/src/main/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoop.kt
Method: buildFeedbackItemRequest (around line 414)

Related: InstructionSection.FeedbackItem already exists in
app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt
and can render per-item doer instructions - just needs to be wired into the
ContextForAgentProviderImpl instruction plan.

