---
id: nid_d1qyvonxndnk9yp68czrb98ki_E
title: "Implement StdinUserQuestionHandler — V1 stdin/stdout question answering"
status: in_progress
deps: [nid_wnpjmnowllgk9oy5ccufgfyr1_E]
links: [nid_y4ick5sgbafgwgeauhu385xq2_E, nid_wnpjmnowllgk9oy5ccufgfyr1_E]
created_iso: 2026-03-19T00:39:58Z
status_updated_iso: 2026-03-19T17:51:21Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, user-question]
---

## Context

Spec: `doc/core/UserQuestionHandler.md` (ref.ap.NE4puAzULta4xlOLh5kfD.E), section "V1: StdinUserQuestionHandler".

The V1 implementation of `UserQuestionHandler` that uses stdin/stdout for human interaction.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/question/StdinUserQuestionHandler.kt`

### StdinUserQuestionHandler

Implements `UserQuestionHandler`. Prints context + question to stdout, reads answer from stdin.

**Stdout format** (from spec):
```
═══════════════════════════════════════════════════════════════
  AGENT QUESTION
  Part: ui_design | Sub-part: impl (DOER)
  HandshakeGuid: handshake.a1b2c3d4-...
═══════════════════════════════════════════════════════════════

How should I handle the responsive layout for mobile devices?

───────────────────────────────────────────────────────────────
  Your answer (press Enter twice to submit):
```

### Behavior

| Aspect | V1 Behavior |
|--------|-------------|
| Input mechanism | `stdin` — `readLine()` in a suspend-friendly wrapper |
| Submission | Two consecutive newlines (empty line) terminates input. Supports multi-line answers. |
| Timeout | **None — intentionally blocks indefinitely.** |
| Context shown | Part name, sub-part name, sub-part role, HandshakeGuid |
| If human is away | Blocks indefinitely. The workflow pauses and resumes when the human returns. |

### Design Decisions

- **No timeout**: Deliberate product decision. The harness is for semi-attended workflows. Failing on inactivity would be worse than waiting.
- `readLine()` must be wrapped in a suspend-friendly manner (e.g., `withContext(Dispatchers.IO)`) to avoid blocking the coroutine dispatcher.
- Uses `println` for user-facing output (NOT Out logging — this is user communication).

## Unit Tests

Test with a fake stdin (e.g., `PipedInputStream`/`PipedOutputStream` or a custom `BufferedReader`):
- GIVEN question context WHEN single-line answer followed by empty line THEN returns answer text
- GIVEN question context WHEN multi-line answer followed by empty line THEN returns joined multi-line text
- GIVEN question context THEN stdout contains part name, sub-part name, role, handshakeGuid, question text

Consider accepting a `BufferedReader` + `PrintWriter` in the constructor for testability (stdin/stdout are defaults).

## Acceptance Criteria

- StdinUserQuestionHandler implements UserQuestionHandler
- Prints formatted context + question to stdout
- Reads multi-line input terminated by double-newline (empty line)
- Blocks indefinitely (no timeout)
- Suspend-friendly stdin reading
- Unit tests with injected reader/writer
- `./test.sh` passes

