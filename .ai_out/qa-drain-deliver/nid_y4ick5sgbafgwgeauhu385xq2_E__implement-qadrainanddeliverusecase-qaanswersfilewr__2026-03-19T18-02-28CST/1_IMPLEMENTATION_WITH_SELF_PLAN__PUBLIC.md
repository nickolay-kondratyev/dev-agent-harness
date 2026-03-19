# Implementation: QaDrainAndDeliverUseCase + QaAnswersFileWriter

## What Was Done

Implemented the QA drain-and-deliver pipeline: drains pending questions from a session's queue, collects answers via UserQuestionHandler, writes them to `qa_answers.md`, and delivers the file path to the agent.

### New Files Created

1. **`app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt`**
   - Interface only (impl is a separate ticket)
   - `sendAndAwaitAck(tmuxSession, sessionEntry, payloadContent)`

2. **`app/src/main/kotlin/com/glassthought/shepherd/core/question/QuestionAndAnswer.kt`**
   - Simple data class pairing question text with answer text

3. **`app/src/main/kotlin/com/glassthought/shepherd/core/question/QaAnswersFileWriter.kt`**
   - Interface + `QaAnswersFileWriterImpl`
   - Writes `qa_answers.md` in spec-compliant markdown format
   - Overwrites on each call (no append)

4. **`app/src/main/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCase.kt`**
   - Drains `sessionEntry.questionQueue` via `poll()` loop
   - Processes questions sequentially, re-checks queue for late arrivals
   - Writes all QA pairs to file, delivers path via `AckedPayloadSender`

5. **`app/src/test/kotlin/com/glassthought/shepherd/core/question/QaAnswersFileWriterTest.kt`**
   - 15 test cases covering: single QA, multiple QA, empty question, overwrite behavior

6. **`app/src/test/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCaseTest.kt`**
   - 24 test cases covering: single/multiple questions, late arrivals during processing, empty queue, spec format verification, delivery assertions
   - Uses `FakeUserQuestionHandler` and `RecordingAckedPayloadSender` fakes

## Key Design Decisions

- **Two UserQuestionContext classes**: The session-package and question-package versions are mapped via `QaDrainAndDeliverUseCase.toQuestionContext()` companion function.
- **Loop-until-empty drain**: Uses `while(true) { poll() ?: break }` pattern to handle questions arriving during answer collection.
- **Batch delivery**: All QA pairs are written to one file and delivered in one `sendAndAwaitAck` call.

## Test Results

- QaAnswersFileWriterTest: 15 tests, 0 failures
- QaDrainAndDeliverUseCaseTest: 24 tests, 0 failures
- Full `./test.sh` passes (BUILD SUCCESSFUL)
