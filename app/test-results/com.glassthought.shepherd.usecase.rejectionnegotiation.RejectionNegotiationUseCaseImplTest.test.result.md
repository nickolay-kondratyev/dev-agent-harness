---
spec: "com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionNegotiationUseCaseImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN doer compliance message is built
  - [PASS] THEN message contains the feedback file path
  - [PASS] THEN message instructs doer to write ADDRESSED
  - [PASS] THEN message states reviewer insists
- GIVEN doer rejected and reviewer accepts the rejection
  - WHEN execute is called
    - [PASS] THEN returns RejectionResult.Accepted
    - [PASS] THEN reviewer instruction file contains rejection reasoning
- GIVEN doer rejected and reviewer insists and doer complies
  - WHEN execute is called
    - [PASS] THEN doer instruction file contains compliance instruction
    - [PASS] THEN returns RejectionResult.AddressedAfterInsistence
- GIVEN doer rejected and reviewer insists but doer still rejects
  - WHEN execute is called
    - [PASS] THEN crash details mention doer defied authority
    - [PASS] THEN returns RejectionResult.AgentCrashed
- GIVEN reviewer crashes during judgment
  - WHEN execute is called
    - [PASS] THEN crash details contain reviewer crash info
    - [PASS] THEN returns RejectionResult.AgentCrashed
- GIVEN reviewer insists and doer responds but leaves no resolution marker
  - WHEN execute is called
    - [PASS] THEN crash details mention missing resolution marker
    - [PASS] THEN returns RejectionResult.AgentCrashed
- GIVEN reviewer insists but doer crashes during compliance
  - WHEN execute is called
    - [PASS] THEN crash details contain doer crash info
    - [PASS] THEN returns RejectionResult.AgentCrashed
- GIVEN reviewer insists but doer signals fail-workflow during compliance
  - WHEN execute is called
    - [PASS] THEN FailedWorkflow contains the doer's reason
    - [PASS] THEN returns RejectionResult.FailedWorkflow
- GIVEN reviewer judgment message is built
  - [PASS] THEN message contains the feedback content
  - [PASS] THEN message contains the feedback file path
  - [PASS] THEN message instructs reviewer to choose pass or needs_iteration
- GIVEN reviewer signals fail-workflow during judgment
  - WHEN execute is called
    - [PASS] THEN FailedWorkflow contains the reason
    - [PASS] THEN returns RejectionResult.FailedWorkflow
