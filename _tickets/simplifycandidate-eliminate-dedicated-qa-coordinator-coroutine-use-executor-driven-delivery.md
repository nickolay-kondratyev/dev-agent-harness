---
id: nid_pcrrocwj7pkx55nnvmbc2c310_E
title: "SIMPLIFY_CANDIDATE: Eliminate dedicated Q&A coordinator coroutine — use executor-driven delivery"
status: open
deps: []
links: []
created_iso: 2026-03-18T15:10:04Z
status_updated_iso: 2026-03-18T15:10:04Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, concurrency, robustness]
---

## Problem

The Q&A architecture (`doc/core/UserQuestionHandler.md`) introduces a dedicated per-session Q&A coordinator coroutine, creating three concurrent actors:
1. **Executor health loop** (reads `isQAPending`, drives signal-await)
2. **HTTP server** (receives agent signals, sets `isQAPending`, forwards to coordinator)
3. **Q&A coordinator coroutine** (owns `QAPendingState`, collects answers, delivers to agent via TMUX)

This three-actor model introduces:
- A third concurrent actor with its own lifecycle management
- `QAPendingState` owned by coordinator while `isQAPending` flag lives on `SessionEntry` — conceptual split
- Ping suppression logic in the health loop (must check `isQAPending` to avoid false-positive crash detection)
- Coordinator startup/shutdown lifecycle tied to session lifecycle
- Cross-actor synchronization boundaries

## Proposed Simplification

Merge Q&A handling into the executor's existing signal-await loop:\n1. Server receives `/signal/question` → queues question(s) into a thread-safe queue on `SessionEntry`\n2. Executor's signal-await loop (which already polls for health) also checks the Q&A queue\n3. When questions are found, executor pauses signal-await, collects answers via `UserQuestionHandler`, delivers answers to agent via TMUX `send-keys`, resumes signal-await\n4. `isQAPending` becomes a simple check: `questionQueue.isNotEmpty()`\n\n## Why This Is Both Simpler AND More Robust\n- **Simpler**: \n  - Two concurrent actors instead of three\n  - No dedicated coordinator coroutine lifecycle\n  - No conceptual split between `isQAPending` (SessionEntry) and `QAPendingState` (coordinator)\n  - Q&A delivery is a step in the executor loop, not a parallel coroutine\n- **More robust**:\n  - Fewer concurrency boundaries = fewer race conditions\n  - No risk of coordinator coroutine dying independently of executor (orphan state)\n  - Ping suppression becomes trivial (executor knows it's handling Q&A because it's the one doing it)\n  - Batch delivery is naturally ordered (executor processes queue, delivers all answers, then resumes)\n- **Preserves all functionality**: Fire-and-forget HTTP endpoint, batch answer delivery, blocking stdin handler — all still work. The change is where the coordination happens, not what coordination happens.

