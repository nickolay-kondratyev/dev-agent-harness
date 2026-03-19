---
id: nid_y4ick5sgbafgwgeauhu385xq2_E
title: "Implement QaDrainAndDeliverUseCase + QaAnswersFileWriter"
status: in_progress
deps: [nid_wnpjmnowllgk9oy5ccufgfyr1_E, nid_d1qyvonxndnk9yp68czrb98ki_E, nid_9kic96nh6mb8r5legcsvt46uy_E]
links: [nid_d1qyvonxndnk9yp68czrb98ki_E, nid_wnpjmnowllgk9oy5ccufgfyr1_E]
created_iso: 2026-03-19T00:40:51Z
status_updated_iso: 2026-03-19T18:02:24Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, user-question]
---

## Context

Spec: `doc/core/UserQuestionHandler.md` (ref.ap.NE4puAzULta4xlOLh5kfD.E), sections "Flow" steps 8-13, "Question + Answer Queuing - Batch Delivery", and "Executor-Driven QA -- Lifecycle".

Also: `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E) for questionQueue details.
Also: `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) for AckedPayloadSender delivery.

This ticket covers the executor-side QA processing logic that runs inside the health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E). The health-aware await loop ticket (nid_qdd1w86a415xllfpvcsf8djab_E) covers the loop structure and QA suppression gate (isQAPending); this ticket covers the actual QA drain-collect-deliver flow that the loop invokes.

## What to Implement

### 1. QaAnswersFileWriter

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/question/QaAnswersFileWriter.kt`

Writes the `comm/in/qa_answers.md` file in the sub-part's `.ai_out/` directory.

**File format** from spec:
```markdown
## QA Answers

### Question 1
> How should I handle the responsive layout for mobile devices?

**Answer:** Use CSS Grid with a mobile-first approach...

### Question 2
> Should I add dark mode support?

**Answer:** Not in V1, focus on core functionality...
```

The file is overwritten on each batch delivery -- git history preserves prior QA.

Input: `List<QuestionAndAnswer>` where `QuestionAndAnswer` is a simple data class holding the question text and answer text.

### 2. QaDrainAndDeliverUseCase

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCase.kt`

Orchestrates the full QA flow when the health-aware await loop detects `questionQueue.isNotEmpty()`:

1. **Drain** `questionQueue` -- take all pending questions
2. **Collect answers** -- call `UserQuestionHandler.handleQuestion()` for each question **sequentially** (V1: one stdin prompt per question)
3. **Check for more** -- if new questions arrived during collection, continue processing
4. **Write** all QA pairs to `comm/in/qa_answers.md` via `QaAnswersFileWriter`
5. **Deliver** the answer file path to the agent via `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E), wrapped in Payload Delivery ACK XML (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E)
6. After delivery ACK received: queue is empty -> isQAPending becomes false -> health monitoring resumes

### Key Behavior

- Questions processed **sequentially** in arrival order
- If new question arrives during answer collection, executor continues until queue is empty
- **ALL answers delivered together** in one batch -- prevents agent from resuming mid-flight
- File is overwritten on each batch delivery

### Constructor Dependencies

```kotlin
class QaDrainAndDeliverUseCase(
    private val userQuestionHandler: UserQuestionHandler,
    private val qaAnswersFileWriter: QaAnswersFileWriter,
    private val ackedPayloadSender: AckedPayloadSender,
    private val outFactory: OutFactory,
)
```

## Unit Tests

- GIVEN single question in queue WHEN drain-and-deliver THEN handler called once AND file written with 1 QA AND delivery sent
- GIVEN 3 questions in queue WHEN drain-and-deliver THEN handler called 3 times sequentially AND file has 3 QA pairs
- GIVEN question arrives during answer collection WHEN processing THEN new question also processed before delivery
- GIVEN answers collected THEN qa_answers.md format matches spec -- numbered questions, blockquoted question text, bold answer prefix
- GIVEN delivery THEN AckedPayloadSender called with correct file path

## Acceptance Criteria

- QaAnswersFileWriter writes spec-compliant markdown
- QaDrainAndDeliverUseCase drains queue, collects answers sequentially, writes file, delivers
- Handles concurrent question arrival during collection -- loop until queue empty
- Unit tests with fake UserQuestionHandler and mock AckedPayloadSender
- `./test.sh` passes

