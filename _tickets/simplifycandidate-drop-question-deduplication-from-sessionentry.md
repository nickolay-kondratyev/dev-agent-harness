---
id: nid_fbfzm0k8h28agsioj1mxycezm_E
title: "SIMPLIFY_CANDIDATE: Drop question deduplication from SessionEntry"
status: open
deps: []
links: []
created_iso: 2026-03-15T01:08:34Z
status_updated_iso: 2026-03-15T01:08:34Z
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

