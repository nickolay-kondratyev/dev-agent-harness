---
spec: "com.glassthought.chainsaw.core.git.BranchNameBuilderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN build
  - WHEN called with a long title and id='nid_abc123'
    - [PASS] THEN branch name ends with '__try-1'
    - [PASS] THEN branch name starts with 'nid_abc123__'
    - [PASS] THEN the slug portion is at most 60 characters
  - WHEN called with blank id
    - [PASS] THEN throws IllegalArgumentException
  - WHEN called with id='TK-001', title='My Feature', tryNumber=1
    - [PASS] THEN returns 'TK-001__my-feature__try-1'
  - WHEN called with id='TK-001', title='My Feature', tryNumber=3
    - [PASS] THEN returns 'TK-001__my-feature__try-3'
  - WHEN called with tryNumber=-1
    - [PASS] THEN throws IllegalArgumentException
  - WHEN called with tryNumber=0
    - [PASS] THEN throws IllegalArgumentException
- GIVEN slugify
  - WHEN called with a simple title 'My Feature'
    - [PASS] THEN returns 'my-feature'
  - WHEN called with a title exceeding 60 characters
    - [PASS] THEN result length is at most 60
  - WHEN called with a title that truncates to end with a hyphen
    - [PASS] THEN result does not end with a hyphen
  - WHEN called with an empty string
    - [PASS] THEN returns 'untitled'
  - WHEN called with consecutive spaces 'fix the bug'
    - [PASS] THEN returns 'fix-the-bug'
  - WHEN called with leading/trailing special chars '---hello---'
    - [PASS] THEN returns 'hello'
  - WHEN called with only special characters '!@#$%^'
    - [PASS] THEN returns 'untitled'
  - WHEN called with special characters 'Fix: bug #123!'
    - [PASS] THEN returns 'fix-bug-123'
  - WHEN called with unicode characters 'café latté'
    - [PASS] THEN non-ascii chars become hyphens producing 'caf-latt'
