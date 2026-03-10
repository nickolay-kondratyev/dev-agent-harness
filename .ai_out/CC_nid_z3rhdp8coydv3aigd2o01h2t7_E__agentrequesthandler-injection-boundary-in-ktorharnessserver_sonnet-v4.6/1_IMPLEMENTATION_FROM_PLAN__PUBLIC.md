# Implementation: AgentRequestHandler Injection Boundary in KtorHarnessServer

## Summary of Changes

### New File: `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequestHandler.kt`

Created `AgentRequestHandler` interface and `NoOpAgentRequestHandler` placeholder:

- `AgentRequestHandler` has 4 suspend methods: `onDone`, `onQuestion`, `onFailed`, `onStatus`
- `onQuestion` returns `String` (the answer to relay back to the agent)
- `NoOpAgentRequestHandler` is a no-behavior placeholder for until the phase runner wires in real logic

### Modified: `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt`

- Added `agentRequestHandler: AgentRequestHandler = NoOpAgentRequestHandler()` parameter to `KtorHarnessServer` (default allows existing call sites to compile unchanged)
- Updated `handleAgentRequest` helper to accept an `action: suspend (T) -> Any` lambda that produces the response body
- Routed each endpoint through the handler:
  - `/done`, `/failed`, `/status` — call handler method, respond `{"status":"ok"}`
  - `/question` — calls `onQuestion`, responds `{"answer": "<returned string>"}`
- Updated KDoc on `KtorHarnessServer` to document `agentRequestHandler` parameter

### Modified: `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`

- Updated `createFixture()` to pass `NoOpAgentRequestHandler()` explicitly to the constructor
- Added new `GIVEN a KtorHarnessServer with a recording handler` describe block with:
  - `RecordingAgentRequestHandler` local test class that records calls
  - `createRecordingFixture()` helper
  - Test: onDone call count is 1 after POST /agent/done
  - Test: onDone receives correct branch value
  - Test: POST /agent/question response body contains handler's answer string

## Test Results

All 17 tests in `KtorHarnessServerTest` pass:

- 14 pre-existing tests: all pass (no regressions)
- 3 new tests:
  - `THEN onDone is invoked with the correct branch`
  - `THEN onDone receives the correct branch value`
  - `THEN response body contains the handler's answer`

Full test suite (all test classes): `BUILD SUCCESSFUL`

## Deviations from Plan

**One assert per test**: The plan's proposed `THEN onDone is invoked with the correct branch` test checked both `doneCalls.size shouldBe 1` AND `doneCalls[0].branch shouldBe "my-branch"` in a single `it` block. Per the testing standards (one assert per test), split this into two separate `it` blocks:
- `THEN onDone is invoked with the correct branch` — checks `doneCalls.size shouldBe 1`
- `THEN onDone receives the correct branch value` — checks `doneCalls[0].branch shouldBe "my-branch"`

## Implementation Complete

All plan items implemented, all tests green.
