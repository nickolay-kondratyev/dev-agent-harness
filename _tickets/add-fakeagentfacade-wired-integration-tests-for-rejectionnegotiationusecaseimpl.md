---
id: nid_f9u9xgyfnfhnjdga74163o1ak_E
title: "Add FakeAgentFacade-wired integration tests for RejectionNegotiationUseCaseImpl"
status: open
deps: []
links: []
created_iso: 2026-03-19T18:57:24Z
status_updated_iso: 2026-03-19T18:57:24Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [unit-test, FakeAgentFacade, rejection-negotiation]
---

Add integration-style unit tests to RejectionNegotiationUseCaseImpl that wire FakeAgentFacade through real ReInstructAndAwaitImpl (instead of the current FakeReInstructAndAwait). This tests the full composition: FakeAgentFacade → ReInstructAndAwaitImpl → RejectionNegotiationUseCaseImpl.

Current state: RejectionNegotiationUseCaseImplTest uses a custom FakeReInstructAndAwait to unit-test the use case. While those tests are valuable, they skip the ReInstructAndAwait→AgentFacade signal mapping layer. FakeAgentFacade-wired tests would catch integration bugs between the two layers.

Create a new test class: RejectionNegotiationWithFakeAgentFacadeTest

File: app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationWithFakeAgentFacadeTest.kt

Dependencies to wire:
- FakeAgentFacade (from app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt)
- ReInstructAndAwaitImpl (from app/src/main/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwait.kt) — takes AgentFacade as constructor param
- RejectionNegotiationUseCaseImpl (from app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt) — takes ReInstructAndAwait as constructor param
- FeedbackFileReader functional interface — use closure-based fake as in existing tests

Test scenarios to cover (each as BDD GIVEN/WHEN/THEN):

1. GIVEN reviewer signals Done(PASS) via AgentFacade → THEN RejectionResult.Accepted
   - Programs FakeAgentFacade.onSendPayloadAndAwaitSignal to return AgentSignal.Done(PASS) for reviewer handle
   - Verifies AgentPayload forwarded correctly through the stack

2. GIVEN reviewer signals Done(NEEDS_ITERATION) and doer signals Done(COMPLETED) with file updated to ADDRESSED
   - Two sequential AgentFacade calls: reviewer then doer
   - Use ArrayDeque in FakeAgentFacade to queue different signals per call
   - Verify both handles get correct payloads

3. GIVEN reviewer signals Done(COMPLETED) — unexpected signal
   - Programs FakeAgentFacade to return AgentSignal.Done(COMPLETED) for reviewer
   - Verifies RejectionResult.AgentCrashed with message about unexpected COMPLETED
   - THIS IS A NEW SCENARIO: not covered in existing tests

4. GIVEN reviewer insists → doer writes SKIPPED resolution
   - Programs FakeAgentFacade for reviewer NEEDS_ITERATION then doer COMPLETED
   - FeedbackFileReader returns SKIPPED content on second read
   - Verifies RejectionResult.AgentCrashed mentioning "SKIPPED"
   - THIS IS A NEW SCENARIO: not covered in existing tests

5. GIVEN reviewer insists → doer writes invalid resolution marker
   - Programs FakeAgentFacade for reviewer NEEDS_ITERATION then doer COMPLETED
   - FeedbackFileReader returns content with "## Resolution: MAYBE_LATER"
   - Verifies RejectionResult.AgentCrashed mentioning "invalid resolution marker"
   - THIS IS A NEW SCENARIO: not covered in existing tests

6. GIVEN reviewer signals Crashed via AgentFacade → THEN AgentCrashed propagates through stack
   - Programs FakeAgentFacade to return AgentSignal.Crashed for reviewer
   - Verifies crash details flow through ReInstructAndAwaitImpl → RejectionNegotiationUseCaseImpl unchanged

7. GIVEN reviewer signals FailWorkflow via AgentFacade → THEN FailedWorkflow propagates
   - Programs FakeAgentFacade to return AgentSignal.FailWorkflow for reviewer
   - Verifies reason string flows through unchanged

8. Verify FakeAgentFacade.sendPayloadCalls records correct AgentPayload for each step
   - Interaction verification: after happy-path insistence flow, sendPayloadCalls should have exactly 2 entries (reviewer + doer)
   - Each entry should have the correct handle

References:
- FakeAgentFacade: app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt
- ReInstructAndAwaitImpl: app/src/main/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwait.kt
- RejectionNegotiationUseCaseImpl: app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt
- Existing tests (for pattern reference): app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt
- FakeAgentFacade usage example: app/src/test/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwaitImplTest.kt

