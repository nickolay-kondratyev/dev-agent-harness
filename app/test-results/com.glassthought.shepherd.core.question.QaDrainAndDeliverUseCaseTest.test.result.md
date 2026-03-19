---
spec: "com.glassthought.shepherd.core.question.QaDrainAndDeliverUseCaseTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN 3 questions in queue
  - WHEN drain-and-deliver is called
    - [PASS] THEN AckedPayloadSender was called once (batch delivery)
    - [PASS] THEN handler was called 3 times
    - [PASS] THEN qa_answers.md contains Question 3
    - [PASS] THEN questions were received in order
- GIVEN answers collected
  - WHEN drain-and-deliver is called
    - [PASS] THEN answer 1 has bold prefix
    - [PASS] THEN answer 2 has bold prefix
    - [PASS] THEN qa_answers.md matches spec format with header
    - [PASS] THEN question 1 blockquote is correct
    - [PASS] THEN question 2 blockquote is correct
- GIVEN delivery
  - WHEN drain-and-deliver is called
    - [PASS] THEN AckedPayloadSender receives correct sessionEntry
    - [PASS] THEN AckedPayloadSender receives correct tmuxAgentSession
    - [PASS] THEN payload content starts with Read QA answers at
- GIVEN empty question queue
  - WHEN drain-and-deliver is called
    - [PASS] THEN AckedPayloadSender was never called
    - [PASS] THEN handler was never called
    - [PASS] THEN no file was written
- GIVEN question arrives during answer collection
  - WHEN drain-and-deliver is called
    - [PASS] THEN both questions were processed
    - [PASS] THEN late arrival question was also included
    - [PASS] THEN only one delivery was made
    - [PASS] THEN qa_answers.md contains both answers
- GIVEN single question in queue
  - WHEN drain-and-deliver is called
    - [PASS] THEN AckedPayloadSender was called once
    - [PASS] THEN handler was called once
    - [PASS] THEN payload references qa_answers.md path
    - [PASS] THEN qa_answers.md does not contain Question 2
    - [PASS] THEN qa_answers.md exists with 1 QA pair
