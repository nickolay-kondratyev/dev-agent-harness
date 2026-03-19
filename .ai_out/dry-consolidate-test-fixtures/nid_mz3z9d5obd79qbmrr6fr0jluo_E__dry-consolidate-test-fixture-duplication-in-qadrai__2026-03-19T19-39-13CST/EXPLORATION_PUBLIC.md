# Exploration: DRY Test Fixture Consolidation

## Files Involved

### Shared Fixture (target)
- `app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionTestFixtures.kt`
  - `noOpCommunicator` (internal)
  - `noOpExistsChecker` (internal)
  - `createTestTmuxAgentSession()` — no params
  - `createTestSessionEntry(partName, subPartName, subPartIndex, questionQueue)` — all with defaults
  - `createTestUserQuestionContext()` — no params, hardcoded "test question?"

### Duplicating File 1: QaDrainAndDeliverUseCaseTest.kt
- `app/src/test/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCaseTest.kt`
- **Private duplicates:**
  - `noOpCommunicator` — exact copy
  - `noOpExistsChecker` — exact copy
  - `createTestTmuxAgentSession()` — exact copy (no params)
  - `createTestSessionEntry(questions: List<UserQuestionContext>)` — converts list to ConcurrentLinkedQueue, same defaults
  - `createPendingQuestion(question: String)` — like `createTestUserQuestionContext()` but takes custom question string

### Duplicating File 2: AckedPayloadSenderTest.kt
- `app/src/test/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSenderTest.kt`
- **Private duplicates:**
  - `noOpExistsChecker` — exact copy
  - `createTestTmuxAgentSession(handshakeGuid, communicator)` — different signature, accepts custom handshakeGuid and communicator
  - `createTestSessionEntry(tmuxAgentSession)` — different signature, accepts custom tmuxAgentSession

### Reference: ShepherdServerTest.kt (already correctly uses shared fixtures)
- `app/src/test/kotlin/com/glassthought/shepherd/core/server/ShepherdServerTest.kt`
- Imports `createTestSessionEntry` from `com.glassthought.shepherd.core.session`

## Key Observations

1. The shared `createTestTmuxAgentSession()` needs optional params for `handshakeGuid` and `communicator` to support AckedPayloadSenderTest's usage.
2. The shared `createTestSessionEntry()` needs an optional `tmuxAgentSession` param for AckedPayloadSenderTest.
3. `createTestUserQuestionContext()` needs an optional `question` param to support QaDrainAndDeliverUseCaseTest's `createPendingQuestion`.
4. QaDrainAndDeliverUseCaseTest's `createTestSessionEntry(questions)` pattern (taking a List and converting to queue) can be handled by the shared `createTestSessionEntry(questionQueue)` with caller converting.
