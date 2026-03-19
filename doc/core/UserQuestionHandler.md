#has-tickets
# UserQuestionHandler / ap.NE4puAzULta4xlOLh5kfD.E

Strategy interface for handling user questions from agents. Decouples the question-answering
mechanism from the server and protocol — the server queues the question on `SessionEntry`,
the executor's health-aware await loop picks it up, collects answers via
`UserQuestionHandler`, and batch-delivers them to the agent.

Previously `ap.DvfGxWtvI1p6pDRXdHI2W.E` — that AP is retained as an alias in the protocol doc.

---

## Interface

```kotlin
interface UserQuestionHandler {
    /**
     * Handle a question from an agent. Returns the answer text.
     * May suspend indefinitely (e.g., waiting for human input).
     */
    suspend fun handleQuestion(context: UserQuestionContext): String
}

data class UserQuestionContext(
    val question: String,
    val partName: String,
    val subPartName: String,
    val subPartRole: SubPartRole,
    val handshakeGuid: HandshakeGuid,
)
```

---

## Architecture — Executor-Driven Q&A Delivery

Q&A handling is **integrated into the executor's health-aware await loop**
(ref.ap.QCjutDexa2UBDaKB3jTcF.E). There are only **two concurrent actors** involved in Q&A:

1. **HTTP server** — receives `/signal/user-question`, queues the question on
   `SessionEntry.questionQueue` (thread-safe)
2. **Executor's health-aware await loop** (inside `sendPayloadAndAwaitSignal`) — on each tick,
   checks `questionQueue`. When questions are found, pauses signal-await, collects answers via
   `UserQuestionHandler`, batch-delivers answers to the agent via `AckedPayloadSender`, then
   resumes signal-await.

### Why Executor-Driven (Not a Separate Coordinator)

1. **Fewer concurrent actors** — two actors instead of three. No dedicated Q&A coordinator
   coroutine with its own lifecycle management.
2. **No conceptual split** — `isQAPending` is a derived property (`questionQueue.isNotEmpty()`),
   not a separate boolean managed by a different actor than the one owning the queue.
3. **Ping suppression is trivial** — the executor knows it's handling Q&A because it's the one
   doing it. No cross-actor synchronization to check if Q&A is active.
4. **No orphan risk** — a separate coordinator coroutine could die independently of the executor,
   leaving `isQAPending = true` forever. With executor-driven delivery, the Q&A lifecycle is
   bound to the executor's lifecycle automatically.
5. **Batch delivery is naturally ordered** — the executor processes the queue, delivers all
   answers, then resumes. No race between coordinator delivery and executor resumption.

> **Clarification**: "fire-and-forget" refers to the HTTP endpoint returning 200 immediately —
> the agent still sits idle awaiting the TMUX answer, it does NOT continue other work.

---

## Flow

1. Agent calls `callback_shepherd.signal.sh user-question "How should I handle X?"`
2. Script POSTs to `/callback-shepherd/signal/user-question` with `handshakeGuid` + question text
3. Server returns **200 immediately** — no blocking
4. Server updates `SessionEntry.lastActivityTimestamp` (resets health timer)
5. Server appends the question to `SessionEntry.questionQueue`
   (ref.ap.igClEuLMC0bn7mDrK41jQ.E) — thread-safe concurrent queue
6. `isQAPending` becomes `true` (derived: `questionQueue.isNotEmpty()`)
7. **Agent waits** for the answer to arrive via TMUX (does not proceed)
8. Executor's health-aware await loop detects `isQAPending == true` →
   **skips** health pings and noActivityTimeout
9. Executor detects non-empty `questionQueue` → drains the queue and calls
   `UserQuestionHandler.handleQuestion()` for each question sequentially
   (V1: stdin prompt per question)
10. When **all queued questions are answered**, executor writes all answers to
    `comm/in/qa_answers.md` in the sub-part's `.ai_out/` directory
11. Executor batch-delivers the answer file path to the agent via `AckedPayloadSender`
    (ref.ap.tbtBcVN2iCl1xfHJthllP.E) — wrapped in Payload Delivery ACK XML
    (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E)
12. After delivery ACK received: `questionQueue` is empty → `isQAPending` becomes `false` →
    health monitoring resumes normally
13. Agent reads the XML wrapper, ACKs the payload, reads the answer file, and continues

**No long-lived HTTP connections.** The answer is delivered via TMUX, not via the HTTP response.
This follows the core principle: HTTP = agent→harness signaling, TMUX = harness→agent delivery.
Delivery confirmation follows the same protocol as all other `send-keys` payloads — see
[Payload Delivery ACK Protocol](agent-to-server-communication-protocol.md#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe)
(ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).

---

## Question + Answer Queuing (Batch Delivery)

Multiple questions may arrive before any are answered. The question queue lives on
`SessionEntry.questionQueue` — a thread-safe concurrent queue. Both questions AND answers are
managed by the executor:

- Server appends questions to `questionQueue` as they arrive via `/signal/user-question`
- Executor drains the queue and collects answers one at a time (one stdin prompt per question, sequentially)
- **ALL answers delivered together** after the full queue is answered — prevents the agent
  from resuming mid-flight while additional answers are still pending
- If a new question arrives while the executor is collecting answers, the queue grows and
  the executor continues processing until the queue is empty

### Answer File Format (`comm/in/qa_answers.md`)

```markdown
## Q&A Answers

### Question 1
> How should I handle the responsive layout for mobile devices?

**Answer:** Use CSS Grid with a mobile-first approach...

### Question 2
> Should I add dark mode support?

**Answer:** Not in V1, focus on core functionality...
```

The file is overwritten on each batch delivery. Git history preserves prior Q&A interactions.

---

## Executor-Driven Q&A — Lifecycle

Q&A handling runs **inside the executor's health-aware await loop**
(ref.ap.QCjutDexa2UBDaKB3jTcF.E), which is owned by `AgentFacadeImpl.sendPayloadAndAwaitSignal`
(ref.ap.9h0KS4EOK5yumssRCJdbq.E). No separate coroutine is launched.

| Aspect | Behavior |
|--------|----------|
| **Scope** | Scoped to the `sendPayloadAndAwaitSignal` call. If the call returns (signal received, crash detected), Q&A processing stops naturally — no orphaned coroutine. |
| **Concurrency** | The server appends to `questionQueue` (thread-safe). The executor reads from it on each tick of the await loop. No separate actor. |
| **Sequential processing** | Questions are presented to the human one at a time, in arrival order. The executor awaits the answer for question N before prompting for question N+1. |
| **Batch delivery** | After draining the queue and collecting all answers, the executor delivers. If a new question arrives during answer collection, the executor continues until the queue is empty. |
| **Post-delivery** | After `AckedPayloadSender` confirms delivery (ACK received), the queue is empty → `isQAPending` becomes `false` (derived). |

### noActivityTimeout During Q&A

Because health checks are suppressed during Q&A, the executor does **NOT** fire
`noActivityTimeout` while `isQAPending` is true. The agent is known-idle; the only "crash"
detectable is session death (which the harness cannot distinguish from a healthy idle agent
without pinging).

If the TMUX session dies during Q&A, the executor will detect this when it attempts to
deliver via `AckedPayloadSender` (send-keys fails or ACK never arrives) — the standard ACK
retry exhaustion path applies (3 attempts, then `AgentSignal.Crashed`). No special handling
beyond what the ACK protocol already provides.

---

## V1: StdinUserQuestionHandler

The V1 implementation uses **stdin/stdout** — prints context + question to stdout, reads
answer from stdin. Blocks the coroutine until the human provides input (no timeout, no
autonomous fallback).

**What the human sees** (stdout):

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

| Aspect | V1 Behavior |
|--------|-------------|
| Input mechanism | `stdin` — `readLine()` in a suspend-friendly wrapper |
| Submission | Two consecutive newlines (empty line) terminates input. Supports multi-line answers. |
| Timeout | **None — intentionally blocks indefinitely.** See design note below. |
| Context shown | Part name, sub-part name, sub-part role, HandshakeGuid |
| If human is away | Blocks indefinitely. The workflow pauses and resumes when the human returns. |

### Design Decision: No Timeout on Stdin Q&A

`StdinUserQuestionHandler` **intentionally has no timeout** and will block indefinitely waiting for
human input. This is a deliberate product decision:

- The harness is designed for semi-attended workflows. A human operator may be in a meeting,
  on a walk, or simply away from the keyboard for an extended period.
- Failing or terminating the workflow due to inactivity would be **worse than waiting** — the
  operator returns to find the work destroyed rather than paused.
- The correct mental model is: **the workflow stops and waits**, just like a human colleague
  holding on a question would wait however long it takes.

**Consequence:** Do not run the harness with `StdinUserQuestionHandler` in fully unattended
environments (e.g., CI with no interactive terminal). For those use cases, a future
`LlmUserQuestionHandler` or `TimeoutWithFallbackHandler` (see
[`doc_v2/user-question-handler-future-strategies.md`](../../doc_v2/user-question-handler-future-strategies.md))
is the right choice — they are explicitly designed for autonomous operation.

### WHY Ping Suppression is Safe During Stdin Q&A

With `StdinUserQuestionHandler`, the human may be away for hours. Without ping suppression, the
health loop would fire pings every 30 minutes — each ping adds a TMUX `send-keys` message to
the agent's context window, which is pure waste: the agent is idle, waiting for the Q&A answer.
Over a multi-hour absence, this could accumulate dozens of pings, consuming context window
capacity that the agent needs for actual work.

With Q&A-aware suppression (`isQAPending` gate — derived from `questionQueue.isNotEmpty()`),
the executor knows the agent is in a known-idle state because it is the one handling Q&A.
No pings needed, no context waste. The agent's context window is preserved for the actual
answer and subsequent work.

---

## Future Strategies (V2+)

V2+ will add autonomous and async question-answering strategies. See
[`doc_v2/user-question-handler-future-strategies.md`](../../doc_v2/user-question-handler-future-strategies.md).

---

## Checks Gated on `isQAPending`

The following executor health-aware await loop behaviors are **suppressed** when
`isQAPending` is true (derived: `questionQueue.isNotEmpty()`):

| Check | Normal behavior | When Q&A pending |
|-------|----------------|-----------------|
| Health ping firing | Fire when `lastActivityTimestamp` stale > `normalActivity` | **Skip** — agent is known-idle awaiting TMUX answer; executor is handling Q&A |
| noActivityTimeout | Declare crash when stale > `normalActivity` + no ping response | **Skip** — agent is known-idle, not crashed |

These gates are trivially implemented because the executor is the single owner of both
health checks and Q&A processing — it knows it is handling Q&A because it is the one
doing it. See the health-aware await loop pseudocode (ref.ap.QCjutDexa2UBDaKB3jTcF.E)
and HealthMonitoring spec (ref.ap.RJWVLgUGjO5zAwupNLhA0.E).
