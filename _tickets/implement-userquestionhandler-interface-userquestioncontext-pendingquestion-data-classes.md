---
id: nid_wnpjmnowllgk9oy5ccufgfyr1_E
title: "Implement UserQuestionHandler interface + UserQuestionContext data class"
status: open
deps: []
links: [nid_d1qyvonxndnk9yp68czrb98ki_E, nid_y4ick5sgbafgwgeauhu385xq2_E]
created_iso: 2026-03-19T00:39:38Z
status_updated_iso: 2026-03-19T00:39:38Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, user-question]
---

## Context

Spec: `doc/core/UserQuestionHandler.md` (ref.ap.NE4puAzULta4xlOLh5kfD.E).

Define the strategy interface and supporting data classes for handling user questions from agents.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/question/`

### 1. UserQuestionHandler interface

```kotlin
interface UserQuestionHandler {
    /**
     * Handle a question from an agent. Returns the answer text.
     * May suspend indefinitely (e.g., waiting for human input).
     */
    suspend fun handleQuestion(context: UserQuestionContext): String
}
```

### 2. UserQuestionContext data class

```kotlin
data class UserQuestionContext(
    val question: String,
    val partName: String,
    val subPartName: String,
    val subPartRole: SubPartRole,
    val handshakeGuid: HandshakeGuid,
)
```

Depends on existing `SubPartRole` enum and `HandshakeGuid` type.

**Note**: `PendingQuestion` data class is covered by ticket nid_5o5wyxuzoz7qrkuq4wuo2gnjr_E (SessionEntry + PendingQuestion data classes).

## Acceptance Criteria

- Interface compiles with suspend function signature
- Data classes compile with correct fields
- `./test.sh` passes
- No implementation of UserQuestionHandler in this ticket (that is StdinUserQuestionHandler ticket)

