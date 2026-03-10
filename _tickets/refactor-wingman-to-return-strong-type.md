---
id: nid_47z6remhu28wwoi4clb1b0pb9_E
title: "refactor wingman to return strong type"
status: open
deps: []
links: []
created_iso: 2026-03-10T23:14:10Z
status_updated_iso: 2026-03-10T23:14:10Z
type: task
priority: 3
assignee: nickolaykondratyev
---


Refactor the string that is returned into a data class that is called `ResumableAgentSessionId`

```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt] Lines=[25-26]
    suspend fun resolveSessionId(guid: HandshakeGuid): String
```

This `ResumableAgentSessionId` will contain agent enum (we should have `AgentType` enum created to contain `CLAUDE_CODE, PI`) as well as the string of the session id.