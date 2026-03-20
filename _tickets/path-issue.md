---
id: nid_gwafum2kyc1kcic7yyla4c4fp_E
title: "PATH issue"
status: in_progress
deps: []
links: []
created_iso: 2026-03-20T20:32:29Z
status_updated_iso: 2026-03-20T20:35:36Z
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