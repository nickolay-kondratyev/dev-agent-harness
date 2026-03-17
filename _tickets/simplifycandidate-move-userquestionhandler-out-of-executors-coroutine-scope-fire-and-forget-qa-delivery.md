---
id: nid_ardsgbv0n6z48ya1y3079ez2u_E
title: "SIMPLIFY_CANDIDATE: Move UserQuestionHandler out of executor's coroutine scope — decoupled Q&A with ping suppression"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T22:01:57Z
status_updated_iso: 2026-03-17T23:02:11Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, user-question, coroutines]
---

## Problem

When an agent asks a question, Q&A is a side-channel signal — the executor stays suspended on `signalDeferred.await()` while the server handles it. However, the current spec has two issues:

1. **Health pings fire during Q&A wait** — after 30 min of no activity, the health loop sends pings to the agent. The agent is sitting idle awaiting a TMUX answer; these pings are pure context-window waste (could be hours of pings if human is slow).

2. **Conceptual coupling** — UserQuestionHandler lifecycle is entangled with the executor's health loop scope, making it harder to reason about and extend.

> **Clarification**: "fire-and-forget" refers to the HTTP endpoint returning 200 immediately — the agent still sits idle awaiting the TMUX answer, it does NOT continue other work.

---

## Proposed Design

### Q&A is a fully independent concern

Move Q&A handling into a **dedicated Q&A coordinator coroutine**, spawned per-session, entirely outside the executor's health-aware await loop:

- Agent calls `/callback-shepherd/signal/user-question` → HTTP returns 200 immediately
- Server enqueues the question into `SessionEntry.pendingQA`
- Server launches (or notifies) the **Q&A coordinator** for this session
- Executor's health-aware loop checks `SessionEntry.isQAPending` → **skips pings** when true
- Context window compaction check also skips while Q&A is pending
- Q&A coordinator collects answers (StdinUserQuestionHandler, LLM, etc.)
- When **all queued questions are answered**, batch-deliver ALL answers together via AckedPayloadSender
- After delivery ACK: clear `pendingQA`, health monitoring and compaction resume normally

### Question + Answer queuing (batch delivery)

Multiple questions may arrive before any are answered. Both questions AND answers are queued:

- Questions enqueued as they arrive (one stdin prompt per question, sequentially)
- Answers collected as humans provide them
- **ALL answers delivered together** after the full queue is answered — prevents agent from resuming mid-flight while additional answers are still pending

### Lifecycle

- Q&A coordinator is scoped to the session. If the session/executor terminates for any reason (noActivityTimeout, /fail-workflow, harness restart), the Q&A coroutine is **cancelled silently** — no answer delivered, no error.
- noActivityTimeout behavior: because pings are suppressed during Q&A, the executor should NOT fire noActivityTimeout while Q&A is pending. The agent is known-idle; the only "crash" detectable is session death.

---

## Key State Changes (SessionEntry)

```
pendingQA: QAPendingState?   // null = no Q&A pending; non-null = questions/answers queued
```

Where `QAPendingState` holds the ordered question queue and collected answers.

The following checks must gate on `isQAPending`:
- Health ping firing (skip if pending)
- Context window compaction trigger (skip if pending)

---

## Relevant Specs

- Agent↔Harness protocol, user-question endpoint: ref.ap.wLpW8YbvqpRdxDplnN7Vh.E
- UserQuestionHandler interface + lifecycle: ref.ap.NE4puAzULta4xlOLh5kfD.E
- SessionsState (GUID→session registry): ref.ap.7V6upjt21tOoCFXA7nqNh.E
- PartExecutor, health-aware await loop: ref.ap.fFr7GUmCYQEV5SJi8p6AS.E
- Context window compaction: ref.ap.8nwz2AHf503xwq8fKuLcl.E

## Relevant Code (not yet implemented — spec-first)

- `UserQuestionHandler` interface + `StdinUserQuestionHandler`
- Server endpoint handler for `/callback-shepherd/signal/user-question`
- `SessionEntry` state
- Health-aware await loop inside `AgentFacadeImpl.sendPayloadAndAwaitSignal`
- Context compaction trigger logic
