---
spec: "com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionNegotiationWithFakeAgentFacadeTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN insistence flow completes through full wired stack
  - WHEN execute completes
    - [PASS] THEN first call is to reviewer handle
    - [PASS] THEN second call is to doer handle
    - [PASS] THEN sendPayloadCalls has exactly 2 entries
- GIVEN reviewer insists and doer writes SKIPPED through full wired stack
  - WHEN execute is called
    - [PASS] THEN crash details mention SKIPPED
    - [PASS] THEN returns RejectionResult.AgentCrashed
- GIVEN reviewer insists and doer writes invalid resolution marker through full wired stack
  - WHEN execute is called
    - [PASS] THEN crash details mention invalid resolution marker
    - [PASS] THEN returns RejectionResult.AgentCrashed
- GIVEN reviewer signals Crashed through full wired stack
  - WHEN execute is called
    - [PASS] THEN crash details flow unchanged from facade
    - [PASS] THEN returns RejectionResult.AgentCrashed
- GIVEN reviewer signals Done(PASS) through full wired stack
  - WHEN execute is called
    - [PASS] THEN payload forwarded to reviewer contains rejection reasoning
    - [PASS] THEN returns RejectionResult.Accepted
- GIVEN reviewer signals FailWorkflow through full wired stack
  - WHEN execute is called
    - [PASS] THEN reason flows unchanged from facade
    - [PASS] THEN returns RejectionResult.FailedWorkflow
- GIVEN reviewer signals NEEDS_ITERATION and doer writes ADDRESSED through full wired stack
  - WHEN execute is called
    - [PASS] THEN doer handle receives second payload
    - [PASS] THEN returns RejectionResult.AddressedAfterInsistence
    - [PASS] THEN reviewer handle receives first payload
- GIVEN reviewer signals unexpected Done(COMPLETED) through full wired stack
  - WHEN execute is called
    - [PASS] THEN crash details mention unexpected COMPLETED
    - [PASS] THEN returns RejectionResult.AgentCrashed
