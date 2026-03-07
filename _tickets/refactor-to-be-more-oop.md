---
id: nid_lfazm1rs99uoj2td5iwljvc7k_E
title: "refactor to be more OOP"
status: in_progress
deps: []
links: []
created_iso: 2026-03-07T23:16:54Z
status_updated_iso: 2026-03-07T23:27:35Z
type: task
priority: 3
assignee: nickolaykondratyev
---




Add interface:
```kt file=[/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/tmux/TmuxCommunicator.kt] Lines=[17-18]
@AnchorPoint("ap.3BCYPiR792a2B8I9ZONDwmvN.E")
class TmuxCommunicator(
```

Add `TmuxSession` which will be OOP class that will carry in itself the 'TmuxCommunicator' interface and will be able to send keys without requireing another argument.

Let's modify TmuxSessionManager to give `TmuxSession` objects instead of session names.
```kt file=[/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/tmux/TmuxSessionManager.kt] Lines=[14-15]
class TmuxSessionManager(
```


Instead of asking session name our TmuxSession will have `exists()` method
```kt file=[/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/main/kotlin/com/glassthought/tmux/TmuxSessionManager.kt] Lines=[80-81]
    suspend fun sessionExists(sessionName: String): Boolean {
```
