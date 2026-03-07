---
spec: "org.example.TmuxCommunicatorIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a tmux session running bash
  - WHEN sendKeys with echo command
    - [PASS] THEN file is created with expected content
