---
closed_iso: 2026-03-19T19:31:25Z
id: nid_g8nrofr7aho65licpjwf9ea48_E
title: "Add missing edge-case tests to RejectionNegotiationUseCaseImplTest"
status: closed
deps: []
links: []
created_iso: 2026-03-19T18:57:45Z
status_updated_iso: 2026-03-19T19:31:25Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [unit-test, rejection-negotiation, edge-cases]
---

Add missing edge-case tests to the EXISTING RejectionNegotiationUseCaseImplTest (using the current FakeReInstructAndAwait approach). These are production code branches that have zero test coverage.

File to edit: app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt

Production code under test: app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt

Missing test scenarios to add:

1. Test 9: Reviewer sends Done(COMPLETED) during judgment → AgentCrashed
   - Production code: RejectionNegotiationUseCaseImpl.kt lines 131-137
   - GIVEN reviewer responds with Done(COMPLETED) during rejection negotiation
   - Program FakeReInstructAndAwait to return ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED)) for reviewer
   - THEN returns RejectionResult.AgentCrashed
   - THEN crash details mention "unexpected COMPLETED signal"
   - This branch is explicitly handled in production but has zero test coverage.

2. Test 10: Reviewer insists → doer writes SKIPPED resolution → AgentCrashed
   - Production code: RejectionNegotiationUseCaseImpl.kt lines 175-179
   - GIVEN reviewer responds NEEDS_ITERATION, doer responds COMPLETED
   - AND feedbackFileReader returns content with "## Resolution: SKIPPED" on second read
   - THEN returns RejectionResult.AgentCrashed
   - THEN crash details mention "SKIPPED"
   - The SKIPPED branch in FeedbackResolution enum is completely untested here.

3. Test 11: Reviewer insists → doer writes invalid resolution marker → AgentCrashed
   - Production code: RejectionNegotiationUseCaseImpl.kt lines 187-191
   - GIVEN reviewer responds NEEDS_ITERATION, doer responds COMPLETED
   - AND feedbackFileReader returns content with "## Resolution: MAYBE_LATER" on second read
   - THEN returns RejectionResult.AgentCrashed
   - THEN crash details mention "invalid resolution marker"
   - The ParseResult.InvalidMarker branch is completely untested.

4. Test 12: Verify exact call count on FakeReInstructAndAwait
   - In the insistence happy path (reviewer NEEDS_ITERATION → doer complies → ADDRESSED):
   - THEN fakeReInstruct.calls should have exactly 2 entries
   - THEN first call is to reviewerHandle, second to doerHandle
   - This interaction verification catches latent bugs where agents are invoked too many/few times.

5. Test 13: Verify feedbackFilePath is forwarded to feedbackFileReader
   - Currently buildFileReader ignores the path argument (uses _ ->)
   - Add a test where the feedbackFileReader captures and asserts the path parameter
   - GIVEN execute is called with a specific feedbackFilePath
   - THEN feedbackFileReader.readContent receives that exact path
   - This ensures a refactoring that breaks path forwarding would be caught.

Use the existing test patterns (BDD GIVEN/WHEN/THEN, AsgardDescribeSpec, one assert per it block).

References:
- Existing tests: app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt
- Production code: app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt
- FeedbackResolutionParser: app/src/main/kotlin/com/glassthought/shepherd/feedback/FeedbackResolutionParser.kt
- FeedbackResolution enum values: ADDRESSED, REJECTED, SKIPPED

