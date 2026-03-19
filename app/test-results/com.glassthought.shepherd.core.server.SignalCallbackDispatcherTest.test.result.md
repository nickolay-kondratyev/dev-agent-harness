---
spec: "com.glassthought.shepherd.core.server.SignalCallbackDispatcherTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a dispatcher
  - WHEN dispatch with missing handshakeGuid field
    - [PASS] THEN message mentions the missing field
    - [PASS] THEN returns BadRequest
- GIVEN a dispatcher AND done action with invalid result
  - WHEN dispatch(done, {handshakeGuid, result=bogus})
    - [PASS] THEN returns BadRequest for invalid result
- GIVEN a dispatcher AND done action without result field
  - WHEN dispatch(done, {handshakeGuid}) without result
    - [PASS] THEN returns BadRequest for missing result
- GIVEN a dispatcher AND fail-workflow action without reason field
  - WHEN dispatch(fail-workflow, {handshakeGuid}) without reason
    - [PASS] THEN returns BadRequest for missing reason
- GIVEN a dispatcher AND unknown action
  - WHEN dispatch with unknown action
    - [PASS] THEN returns BadRequest for unknown action
- GIVEN a registered session
  - WHEN dispatch(self-compacted, {handshakeGuid})
    - [PASS] THEN completes the signalDeferred with SelfCompacted
    - [PASS] THEN returns Success with AgentSignal.SelfCompacted
    - [PASS] THEN updates lastActivityTimestamp
- GIVEN a registered session AND done signal with completed result
  - WHEN dispatch(done, {handshakeGuid, result=completed})
    - [PASS] THEN completes the signalDeferred
    - [PASS] THEN returns Success with AgentSignal.Done(COMPLETED)
- GIVEN a registered session AND done signal with needs_iteration result
  - WHEN dispatch(done, {handshakeGuid, result=needs_iteration})
    - [PASS] THEN returns Success with AgentSignal.Done(NEEDS_ITERATION)
- GIVEN a registered session AND done signal with pass result
  - WHEN dispatch(done, {handshakeGuid, result=pass})
    - [PASS] THEN returns Success with AgentSignal.Done(PASS)
- GIVEN a registered session AND fail-workflow signal
  - WHEN dispatch(fail-workflow, {handshakeGuid, reason})
    - [PASS] THEN completes the signalDeferred with FailWorkflow
    - [PASS] THEN returns Success with AgentSignal.FailWorkflow
    - [PASS] THEN updates lastActivityTimestamp
- GIVEN a registered session with old timestamp
  - WHEN dispatch(self-compacted, {handshakeGuid})
    - [PASS] THEN lastActivityTimestamp is updated to the fixed clock instant
- GIVEN empty SessionsState
  - WHEN dispatch with unknown handshakeGuid
    - [PASS] THEN SessionNotFound contains the unknown guid
    - [PASS] THEN returns SessionNotFound
