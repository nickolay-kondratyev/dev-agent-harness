---
closed_iso: 2026-03-11T21:09:20Z
id: nid_d47u5pku4ldixx23tyggd29ep_E
title: "Implement ResumeTmuxAgentSessionUseCase"
status: closed
deps: []
links: []
created_iso: 2026-03-11T00:30:19Z
status_updated_iso: 2026-03-11T21:09:20Z
type: feature
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [agent, tmux, resume]
---

Implement the use case for resuming an existing agent session from a saved ResumableAgentSessionId.

Design sketch:
- ResumeTmuxAgentSessionUseCase takes ResumableAgentSessionId + workingDir
- Builds resume command: bash -c 'cd <workingDir> && claude --resume <sessionId>'\n- Creates tmux session with the resume command\n- Returns TmuxAgentSession (no GUID handshake needed since session ID is already known)\n\nKey files for context:\n- app/src/main/kotlin/com/glassthought/shepherd/core/agent/SpawnTmuxAgentSessionUseCase.kt (parallel pattern)\n- app/src/main/kotlin/com/glassthought/shepherd/core/agent/TmuxAgentSession.kt\n- app/src/main/kotlin/com/glassthought/shepherd/core/wingman/ResumableAgentSessionId.kt\n- app/src/main/kotlin/com/glassthought/shepherd/core/tmux/TmuxSessionManager.kt\n\nDeferred from ticket nid_uwof1vfp1tcqxzyoh5citkz5o_E (wire-up-tmuxagentsession-spawn-flow).

