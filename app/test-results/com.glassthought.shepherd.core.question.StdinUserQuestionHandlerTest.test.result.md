---
spec: "com.glassthought.shepherd.core.question.StdinUserQuestionHandlerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN question context
  - WHEN multi-line answer followed by empty line
    - [PASS] THEN returns joined multi-line text
  - WHEN prompt is displayed
    - [PASS] THEN stdout contains AGENT QUESTION header
    - [PASS] THEN stdout contains handshakeGuid
    - [PASS] THEN stdout contains part name
    - [PASS] THEN stdout contains question text
    - [PASS] THEN stdout contains sub-part name and role
    - [PASS] THEN stdout contains submission instructions
  - WHEN reader returns null (EOF)
    - [PASS] THEN returns empty string
  - WHEN single-line answer followed by empty line
    - [PASS] THEN returns the answer text
