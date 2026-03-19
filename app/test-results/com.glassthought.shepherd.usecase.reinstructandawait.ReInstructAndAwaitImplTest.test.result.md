---
spec: "com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwaitImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN agent crashes
  - WHEN execute is called
    - [PASS] THEN Crashed contains the crash details
    - [PASS] THEN returns Crashed
- GIVEN agent responds with Done(COMPLETED)
  - WHEN execute is called
    - [PASS] THEN Responded contains Done(COMPLETED)
    - [PASS] THEN returns Responded
- GIVEN agent responds with Done(NEEDS_ITERATION)
  - WHEN execute is called
    - [PASS] THEN returns Responded with Done(NEEDS_ITERATION)
- GIVEN agent responds with Done(PASS)
  - WHEN execute is called
    - [PASS] THEN returns Responded with Done(PASS)
- GIVEN agent signals SelfCompacted (unexpected)
  - WHEN execute is called
    - [PASS] THEN Crashed details mention unexpected SelfCompacted
    - [PASS] THEN returns Crashed (SelfCompacted should not reach this class)
- GIVEN agent signals fail-workflow
  - WHEN execute is called
    - [PASS] THEN FailedWorkflow contains the reason
    - [PASS] THEN returns FailedWorkflow
- GIVEN execute is called with a message
  - WHEN the facade receives the call
    - [PASS] THEN the correct handle is passed to the facade
    - [PASS] THEN the message is converted to AgentPayload with correct path
