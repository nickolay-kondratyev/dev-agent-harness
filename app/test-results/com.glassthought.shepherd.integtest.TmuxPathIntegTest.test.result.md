---
spec: "com.glassthought.shepherd.integtest.TmuxPathIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a tmux command built by ClaudeCodeAdapter with real CallbackScriptsDir
  - WHEN the command is executed in a tmux session with claude replaced by echo PATH
    - [PASS] THEN the captured PATH contains the callback scripts directory
