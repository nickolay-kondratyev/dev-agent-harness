---
closed_iso: 2026-03-10T23:30:20Z
id: nid_47z6remhu28wwoi4clb1b0pb9_E
title: "refactor wingman to return strong type"
status: closed
deps: []
links: []
created_iso: 2026-03-10T23:14:10Z
status_updated_iso: 2026-03-10T23:30:20Z
type: task
priority: 3
assignee: nickolaykondratyev
---


Refactor the string that is returned into a data class that is called `ResumableAgentSessionId`

```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/shepherd/core/wingman/Wingman.kt] Lines=[25-26]
    suspend fun resolveSessionId(guid: HandshakeGuid): String
```

This `ResumableAgentSessionId` will contain agent enum (we should have `AgentType` enum created to contain `CLAUDE_CODE, PI`) as well as the string of the session id.
## Notes

**2026-03-10T23:30:20Z**

Completed refactoring. Created AgentType enum (CLAUDE_CODE, PI) and ResumableAgentSessionId data class. Updated Wingman interface, ClaudeCodeWingman implementation, and ClaudeCodeWingmanTest (12/12 pass). Review: PASS. Follow-up ticket nid_zt03ntko0lyz93yhhtob9sa0p_E created for pre-existing var guidScanner immutability issue in ClaudeCodeWingman.
