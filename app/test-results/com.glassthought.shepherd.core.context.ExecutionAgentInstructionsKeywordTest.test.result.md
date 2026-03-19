---
spec: "com.glassthought.shepherd.core.context.ExecutionAgentInstructionsKeywordTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a doer instruction request on iteration 1
  - WHEN instructions are assembled
    - [PASS] THEN contains 'ack-payload' signal
    - [PASS] THEN contains 'completed' done result for doer
    - [PASS] THEN contains 'done' signal
    - [PASS] THEN contains 'fail-workflow' signal
    - [PASS] THEN contains 'ping-ack' signal
    - [PASS] THEN contains 'user-question' signal
    - [PASS] THEN contains WHY-NOT keyword
    - [PASS] THEN contains callback example with 'completed' (not 'pass' or 'needs_iteration')
    - [PASS] THEN contains callback signal script name
    - [PASS] THEN contains part name
    - [PASS] THEN contains payload ACK XML tag
    - [PASS] THEN contains role name
    - [PASS] THEN contains ticket content
    - [PASS] THEN does NOT contain query script (execution agents have no queries)
- GIVEN a reviewer instruction request on iteration 2 with feedback
  - WHEN instructions are assembled
    - [PASS] THEN contains 'addressed' feedback status
    - [PASS] THEN contains 'critical' severity
    - [PASS] THEN contains 'important' severity
    - [PASS] THEN contains 'needs_iteration' done result
    - [PASS] THEN contains 'optional' severity
    - [PASS] THEN contains 'pass' done result
    - [PASS] THEN contains 'pending' feedback status
    - [PASS] THEN contains 'rejected' feedback status
    - [PASS] THEN contains WHY-NOT in feedback format
    - [PASS] THEN contains addressed feedback file content
    - [PASS] THEN contains doer's PUBLIC.md content
    - [PASS] THEN contains rejected feedback file content
    - [PASS] THEN contains severity prefix for critical
