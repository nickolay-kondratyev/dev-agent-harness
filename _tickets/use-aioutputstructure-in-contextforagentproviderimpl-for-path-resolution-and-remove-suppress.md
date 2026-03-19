---
id: nid_fv7og578z80kw90kb7bnst9sm_E
title: "Use AiOutputStructure in ContextForAgentProviderImpl for path resolution and remove @Suppress"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T22:51:36Z
status_updated_iso: 2026-03-19T22:59:28Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ai-out, context-provider, cleanup]
---

ContextForAgentProviderImpl now has AiOutputStructure injected as constructor dependency
but it is not yet used for path resolution (marked with @Suppress("UnusedPrivateProperty")).

The suppress was added in the wiring ticket (nid_7xzhkw4pw5sc5hqh80cvsotdc_E) as a transitional state.

What to do:
1. Use aiOutputStructure for PRIVATE.md path resolution (executionPrivateMd/planningPrivateMd) instead of receiving explicit paths via AgentInstructionRequest
2. Remove the @Suppress("UnusedPrivateProperty") annotation
3. Update tests to verify the path resolution uses AiOutputStructure

File: app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt

