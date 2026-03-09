---
spec: "org.example.TmuxCommunicatorIntegTest"
status: FAILED
failed: 1
skipped: 0
---

## Failed Tests

- GIVEN a tmux session running bash > WHEN sendKeys with echo command > THEN file is created with expected content
  - Error: Cannot run program "tmux": Exec failed, error: 2 (No such file or directory) 

- GIVEN a tmux session running bash
  - WHEN sendKeys with echo command
    - [FAIL] THEN file is created with expected content
