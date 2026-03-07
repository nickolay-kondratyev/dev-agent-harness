---
id: nid_zj8rww86omnzhz23lzk8znyf0_E
title: "Try to run tmux with claude code in it"
status: open
deps: []
links: []
created_iso: 2026-03-07T19:10:48Z
status_updated_iso: 2026-03-07T19:10:48Z
type: task
priority: 3
assignee: nickolaykondratyev
---

We are able to run Claude from kotlin with interactivity (IF we are not running under Gradle).

What we now want is to be able to run brand new tmux session from kotlin lets name it something like agent-harness__$XX

And run Claude in that tmux session.

### Context: this works for interactive claude running from kotlin
```
#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  ./gradlew :app:installDist
  ./app/build/install/app/bin/app
}

main "${@}"
```

### Context
We will want to use tmux to be able to send keystrokes to it.

We will want to have TmuxCommunicator class that we will use to send keystrokes to tmux session. So as part of this let's modify our run.sh (within kotlin) to 
1. create new tmux session with name agent-harness__$XX
   2. Print in kotlin the tmux session name.
2. run claude in that tmux session
3. send keystores through TmuxCommunicator to that tmux session (just say something like "Write 'hello world from tmux' to /tmp/out so that we can test that claude worked)