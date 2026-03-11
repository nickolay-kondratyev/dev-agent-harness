---
closed_iso: 2026-03-11T22:51:36Z
id: nid_nwg1em2siekphpqeeuhrtl5wk_E
title: "Fix logging violations in SpawnTmuxAgentSessionUseCaseIntegTest"
status: closed
deps: []
links: []
created_iso: 2026-03-11T14:49:06Z
status_updated_iso: 2026-03-11T22:51:36Z
type: chore
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

In app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt lines 83-86, there are two logging violations:

1. Values embedded in message string (violates out_logging_patterns.md - use Val with ValType instead):
```kotlin
val message = "Spawned tmux session [${agentSession.tmuxSession.name}] with GUID [${agentSession.resumableAgentSessionId.sessionId}]"
println(message)
out.info(message)
```

2. `println` used for logging (println is only allowed for user communication, not logging).

Fix should use structured logging:
```kotlin
out.info("spawned_tmux_session",
    Val(agentSession.tmuxSession.name, ValType.STRING_USER_AGNOSTIC),
    Val(agentSession.resumableAgentSessionId.sessionId, ValType.STRING_USER_AGNOSTIC),
)
```
And remove the `println` call entirely.

