---
spec: "com.glassthought.shepherd.core.question.QaAnswersFileWriterTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN 3 QA pairs
  - WHEN written to file
    - [PASS] THEN all answers are present
    - [PASS] THEN all questions are present
    - [PASS] THEN content contains Question 1
    - [PASS] THEN content contains Question 2
    - [PASS] THEN content contains Question 3
- GIVEN QA pair with empty question text
  - WHEN written to file
    - [PASS] THEN answer is present
    - [PASS] THEN blockquote line contains empty quote marker
    - [PASS] THEN file is still written with correct structure
- GIVEN file already exists
  - WHEN written twice
    - [PASS] THEN file is overwritten with latest content
    - [PASS] THEN old content is gone
- GIVEN single QA pair
  - WHEN written to file
    - [PASS] THEN content contains Question 1 heading
    - [PASS] THEN content contains answer with bold prefix
    - [PASS] THEN content contains question text in blockquote
    - [PASS] THEN content starts with QA Answers header
    - [PASS] THEN file is named qa_answers.md
