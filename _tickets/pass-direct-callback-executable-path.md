---
id: nid_piz5dmri01i09ey16nx2i3nc0_E
title: "Pass direct callback executable path"
status: in_progress
deps: []
links: []
created_iso: 2026-03-20T21:49:25Z
status_updated_iso: 2026-03-20T21:52:25Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


We are running into a problem that for some reason the temp directory that holds the callback_shepherd.signal.sh is not being reflected into the PATH of claude code when it is running of tmux even though the handshake GUID environment variable is in the path. 

Lets SIMPLIFY out this problem and just pass the EXACT path to the executable to callback_shepherd.signal.sh when we spin up TMUX CLaude code so in instructions we will just have the EXACT full path to the callback_shepherd.signal.sh so that even when PATH does not work we are able to call the callback_shepherd.signal.sh just fine since claude code will know where it is exactly.