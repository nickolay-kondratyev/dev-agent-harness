---
spec: "com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwaitImplTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN AgentFacade throws IllegalStateException
  - WHEN execute is called
    - [PASS] THEN the exception propagates uncaught
- GIVEN agent crashes
  - WHEN execute is called
    - [PASS] THEN Crashed contains the crash details
    - [PASS] THEN returns Crashed
- GIVEN agent responds NEEDS_ITERATION then COMPLETED on sequential calls
  - WHEN execute is called twice on the same handle
    - [PASS] THEN first call payload has first message path
    - [PASS] THEN first call references the same handle
    - [PASS] THEN second call payload has second message path
    - [PASS] THEN second call references the same handle
    - [PASS] THEN sendPayloadCalls has exactly 2 entries
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
- GIVEN execute is called with a message path
  - WHEN verifying payload instructionFilePath with explicit Path equality
    - [PASS] THEN instructionFilePath equals Path.of(message)
- GIVEN two different SpawnedAgentHandles
  - WHEN execute is called once per handle
    - [PASS] THEN first sendPayloadCall references the doer handle
    - [PASS] THEN second sendPayloadCall references the reviewer handle
