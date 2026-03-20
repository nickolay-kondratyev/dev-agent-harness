---
closed_iso: 2026-03-20T21:17:56Z
id: nid_gwafum2kyc1kcic7yyla4c4fp_E
title: "PATH issue"
status: closed
deps: []
links: []
created_iso: 2026-03-20T20:32:29Z
status_updated_iso: 2026-03-20T21:17:56Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

We are running into handshake issue where the callback scripts are not on the PATH

WHEN asked what is the path of agent that was spawned this is what it replied:

```
_ /home/node/.nvm/versions/node/v22.22.1/bin
  /opt/homebrew/opt/ruby@2.7/bin
  /home/linuxbrew/.linuxbrew/bin
  /home/linuxbrew/.linuxbrew/sbin
  /usr/local/bin
  /usr/bin
  /bin
  /usr/local/games
  /usr/games
  /Users/nkondrat/vintrin-env/sh/scripts/thorg/api  (x4)
  /home/node/.local/bin
  /home/node/tools/idea-IU-252.28238.7/bin
  /Users/vintrin/software/flutter/bin
  /Users/vintrin/software/kotlin-native-macos-x86_64-1.8.0/bin

  The callback_shepherd.signal.sh script isn't on PATH. It's located at app/src/main/resources/scripts/callback_shepherd.signal.sh in the repo.
```

For some reason the path is NOT being set as expected.

I am thinking to have a focused integration test that doesn't start TMUX agent and just makes sure that the PATH is setup correctly within the TMUX that we start. 
## Notes

**2026-03-20T21:17:51Z**

## Resolution

Added a focused integration test `TmuxPathIntegTest` that verifies the PATH environment variable is correctly set inside tmux sessions.

### What was done:
- Created `app/src/test/kotlin/com/glassthought/shepherd/integtest/TmuxPathIntegTest.kt`
- The test uses `ClaudeCodeAdapter.buildStartCommand()` to build the real command
- Replaces the `claude --model ...` portion with `echo $PATH > tmpFile`
- Starts a real tmux session via `TmuxSessionManager`
- Verifies the captured PATH contains the callback scripts directory
- Gated with `isIntegTestEnabled()` (requires tmux, does NOT require LLM)

### Root cause analysis:
The previous fix (merged to main via ticket nid_kzz296dqtpojvf3gp29827xtk_E) introduced `CallbackScriptsDir` validated type with fail-fast validation at startup. The PATH export mechanism in `ClaudeCodeAdapter.buildStartCommand()` was already correct. The new integration test now verifies this end-to-end through tmux.

### All tests pass:
- Unit tests: BUILD SUCCESSFUL
- Integration test: BUILD SUCCESSFUL
- All tmux-related integration tests: BUILD SUCCESSFUL
