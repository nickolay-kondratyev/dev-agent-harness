---
spec: "org.example.InteractiveProcessRunnerTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN InteractiveProcessResult
  - WHEN constructed with exitCode 42
    - [PASS] THEN exitCode is 42
  - WHEN constructed with interrupted=true
    - [PASS] THEN interrupted is true
- GIVEN InteractiveProcessRunner
  - AND a runner instance
    - WHEN runInteractive is called with a command that exits non-zero
      - [PASS] THEN exit code is 1
    - WHEN runInteractive is called with non-interactive echo
      - [PASS] THEN exit code is 0
      - [PASS] THEN interrupted is false
  - [PASS] WHEN constructed THEN no error
