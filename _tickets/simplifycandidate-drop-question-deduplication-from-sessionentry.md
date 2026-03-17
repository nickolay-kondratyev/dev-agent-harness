---
closed_iso: 2026-03-17T19:57:10Z
id: nid_fbfzm0k8h28agsioj1mxycezm_E
title: "SIMPLIFY_CANDIDATE: Drop question deduplication from SessionEntry"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:08:34Z
status_updated_iso: 2026-03-17T19:57:10Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, user-interaction]
---

The UserQuestionHandler spec (doc/core/UserQuestionHandler.md) and SessionsState spec (doc/core/SessionsState.md) include question deduplication by (handshakeGuid, question) text on the SessionEntry.

This handles an edge case: agent sends the same question twice (e.g., due to context window compaction causing it to forget it already asked).

In practice, asking the human the same question twice is harmless — the human answers again (likely the same answer) and the agent continues. The cost of the duplicate is ~30 seconds of human time in a rare edge case.

The cost of the deduplication logic:
- pendingQuestions map on SessionEntry (ref.ap.7V6upjt21tOoCFXA7nqNh.E)
- Dedup lookup on every incoming question
- Answer routing back to the correct deferred
- Additional state to manage and test

Proposal: Remove deduplication. Every question goes to the human. If it is a duplicate, the human answers it again.

This is simpler (less state, fewer code paths) and equally robust (no information is lost, no incorrect behavior).

Files affected:
- doc/core/UserQuestionHandler.md (remove dedup section)
- doc/core/SessionsState.md (simplify SessionEntry — remove pendingQuestions or simplify to single question)
- UserQuestionHandler implementation
- SessionEntry data class


## Notes

**2026-03-17T19:57:18Z**

Removed question deduplication from specs only (task said focus on spec not code). Changes:
- doc/core/UserQuestionHandler.md: removed dedup section (ap.Girgb4gaq2aecYTHjUj8a.E), simplified Flow steps 3-11 to 3-10
- doc/core/SessionsState.md: removed pendingQuestions field from SessionEntry table
- doc/core/agent-to-server-communication-protocol.md: removed dedup note from user-question endpoint row
- doc/high-level.md: removed dedup note from Q&A mode row
- doc/core/PartExecutor.md: removed dedup AP reference from user-question side-channel row
- doc/core/AgentInteraction.md: updated R2 heading to drop pendingQuestions reference
