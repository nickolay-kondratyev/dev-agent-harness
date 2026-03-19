# Exploration: QaDrainAndDeliverUseCase + QaAnswersFileWriter

## Key Findings

### Existing Types
- `UserQuestionHandler` — `fun interface` in `core/question/`, V1 impl: `StdinUserQuestionHandler`
- `UserQuestionContext` — in `core/question/` (question text + part/subPart/role/handshakeGuid)
- `PendingQuestion` — in `core/session/` (question + context)
- `SessionEntry` — has `questionQueue: ConcurrentLinkedQueue<PendingQuestion>` and derived `isQAPending`
- `AiOutputStructure` — path resolution for `.ai_out/` tree; has `executionCommInDir()` and `planningCommInDir()`

### AckedPayloadSender — NOT YET IN CODE
- Interface is only in spec (`doc/core/agent-to-server-communication-protocol.md`)
- Separate open ticket exists: `implement-ackedpayloadsender-wrap-send-keys-ack-await-retry.md`
- Signature: `sendAndAwaitAck(tmuxSession, sessionEntry, payloadContent)`
- **Decision needed:** We must create the interface now (code depends on it), even though impl is separate ticket

### Note: Duplicate UserQuestionContext
- `com.glassthought.shepherd.core.question.UserQuestionContext` (used by UserQuestionHandler)
- `com.glassthought.shepherd.core.session.UserQuestionContext` (used by PendingQuestion)
- These are identical but in different packages. PendingQuestion.context is the session one.

### File Locations
- New code: `app/src/main/kotlin/com/glassthought/shepherd/core/question/`
- Tests: `app/src/test/kotlin/com/glassthought/shepherd/core/question/`
