# DRY Consolidate Test Fixture Duplication

## What Was Done

Eliminated duplicated test fixture code in `QaDrainAndDeliverUseCaseTest.kt` and `AckedPayloadSenderTest.kt` by reusing the shared `SessionTestFixtures.kt`.

### Changes

1. **Enhanced `SessionTestFixtures.kt`** with optional parameters:
   - `createTestTmuxAgentSession()` now accepts optional `handshakeGuid`, `communicator`, and `existsChecker`
   - `createTestSessionEntry()` now accepts optional `tmuxAgentSession`
   - `createTestUserQuestionContext()` now accepts optional `question`

2. **Cleaned up `QaDrainAndDeliverUseCaseTest.kt`**:
   - Removed private `noOpCommunicator`, `noOpExistsChecker`, `createTestTmuxAgentSession()`, `createTestSessionEntry()`, `createPendingQuestion()`
   - Now imports shared fixtures from `com.glassthought.shepherd.core.session.*`
   - Uses `createTestUserQuestionContext(question = ...)` instead of `createPendingQuestion()`
   - Uses `ConcurrentLinkedQueue(listOf(...))` at call sites instead of a local helper
   - Kept test-specific fakes: `FakeUserQuestionHandler`, `RecordingAckedPayloadSender`

3. **Cleaned up `AckedPayloadSenderTest.kt`**:
   - Removed private `noOpExistsChecker`, `createTestTmuxAgentSession()`, `createTestSessionEntry()`
   - Now imports shared fixtures with named parameters: `createTestTmuxAgentSession(handshakeGuid = ..., communicator = ...)` and `createTestSessionEntry(tmuxAgentSession = ...)`
   - Kept test-specific fake: `SpyTmuxCommunicator`

### Test Results

All tests pass (`BUILD SUCCESSFUL`). No behavioral changes -- purely a DRY refactoring.
