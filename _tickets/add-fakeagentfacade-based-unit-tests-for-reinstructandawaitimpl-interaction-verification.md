---
id: nid_s7dk275tezu0dgxg32wpb9kpk_E
title: "Add FakeAgentFacade-based unit tests for ReInstructAndAwaitImpl — interaction verification"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T18:58:01Z
status_updated_iso: 2026-03-19T19:38:54Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [unit-test, FakeAgentFacade, reinstructandawait]
---

Add additional unit tests to ReInstructAndAwaitImplTest that verify interaction details and sequential call behavior using FakeAgentFacade.

Current state: ReInstructAndAwaitImplTest already covers all AgentSignal variants (Done variants, Crashed, FailWorkflow, SelfCompacted) and basic payload forwarding. The missing tests are for interaction verification and sequential behavior.

File to edit: app/src/test/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwaitImplTest.kt

New test scenarios:

1. GIVEN multiple sequential execute calls on same handle → THEN FakeAgentFacade.sendPayloadCalls records each call in order
   - Program FakeAgentFacade with ArrayDeque to return different signals per call (e.g., NEEDS_ITERATION then COMPLETED)
   - Call execute twice with same handle
   - Verify sendPayloadCalls has exactly 2 entries with correct handles
   - Verify each call received the correct distinct AgentPayload

2. GIVEN execute is called → THEN sendPayloadCalls[0].payload.instructionFilePath matches the message path
   - While existing tests verify this, add explicit Path equality assertion (not just containment)
   - The existing test uses "shouldBe" on payload but adding explicit path resolution test strengthens the contract

3. GIVEN multiple execute calls with different handles → THEN each call records the correct handle
   - Create two different SpawnedAgentHandles (doer + reviewer)
   - Call execute once per handle
   - Verify sendPayloadCalls[0].handle == doerHandle and sendPayloadCalls[1].handle == reviewerHandle
   - This tests that handle identity is preserved through the adaptor

4. GIVEN AgentFacade.sendPayloadAndAwaitSignal throws exception → THEN exception propagates uncaught
   - Program FakeAgentFacade to throw RuntimeException
   - Verify ReInstructAndAwaitImpl does NOT swallow exceptions
   - This is important because the class should NOT catch-and-wrap unexpected exceptions

References:
- Existing tests: app/src/test/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwaitImplTest.kt
- Production code: app/src/main/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwait.kt
- FakeAgentFacade: app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt

