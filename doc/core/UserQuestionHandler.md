# UserQuestionHandler / ap.NE4puAzULta4xlOLh5kfD.E

Strategy interface for handling user questions from agents. Decouples the question-answering
mechanism from the server and protocol — the server enqueues the question, a dedicated
**Q&A coordinator** collects answers, and batch-delivers them to the agent.

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

## Architecture — Q&A as a Fully Independent Concern

Q&A handling is **decoupled from the executor's coroutine scope**. The executor's health-aware
await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) does not participate in Q&A — it only checks
`SessionEntry.isQAPending` to suppress health pings and noActivityTimeout.

### Why Decoupled

1. **Health pings fire during Q&A wait** — without suppression, after 30 min of no activity,
   the health loop sends pings to the agent. The agent is sitting idle awaiting a TMUX answer;
   these pings are pure context-window waste (could be hours of pings if the human is slow).

2. **Conceptual coupling** — Q&A lifecycle was previously entangled with the executor's health
   loop scope, making it harder to reason about and extend.

> **Clarification**: "fire-and-forget" refers to the HTTP endpoint returning 200 immediately —
> the agent still sits idle awaiting the TMUX answer, it does NOT continue other work.

---

## Flow

1. Agent calls `callback_shepherd.signal.sh user-question "How should I handle X?"`
2. Script POSTs to `/callback-shepherd/signal/user-question` with `handshakeGuid` + question text
3. Server returns **200 immediately** — no blocking
4. Server updates `SessionEntry.lastActivityTimestamp` (resets health timer)
5. Server sets `SessionEntry.isQAPending = true`
   (ref.ap.igClEuLMC0bn7mDrK41jQ.E) and forwards the question to the **Q&A coordinator**
6. Q&A coordinator enqueues the question into its internal `QAPendingState`
   (creates state if first question, appends if subsequent)
7. **Agent waits** for the answer to arrive via TMUX (does not proceed)
8. Executor's health-aware await loop detects `isQAPending == true` →
   **skips** health pings and noActivityTimeout
9. Q&A coordinator calls `UserQuestionHandler.handleQuestion()` for each queued question
   sequentially (V1: stdin prompt per question)
10. When **all queued questions are answered**, coordinator writes all answers to
    `comm/in/qa_answers.md` in the sub-part's `.ai_out/` directory
11. Coordinator batch-delivers the answer file path to the agent via `AckedPayloadSender`
    (ref.ap.tbtBcVN2iCl1xfHJthllP.E) — wrapped in Payload Delivery ACK XML
    (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E)
12. After delivery ACK received: coordinator sets `SessionEntry.isQAPending = false` →
    health monitoring resumes normally
13. Agent reads the XML wrapper, ACKs the payload, reads the answer file, and continues

**No long-lived HTTP connections.** The answer is delivered via TMUX, not via the HTTP response.
This follows the core principle: HTTP = agent→harness signaling, TMUX = harness→agent delivery.
Delivery confirmation follows the same protocol as all other `send-keys` payloads — see
[Payload Delivery ACK Protocol](agent-to-server-communication-protocol.md#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe)
(ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).

---

## Question + Answer Queuing (Batch Delivery)

Multiple questions may arrive before any are answered. The Q&A coordinator owns the internal
question/answer queue (`QAPendingState`). Both questions AND answers are queued:

- Questions forwarded by server to coordinator, which enqueues them as they arrive (one stdin prompt per question, sequentially)
- Answers collected as humans provide them
- **ALL answers delivered together** after the full queue is answered — prevents the agent
  from resuming mid-flight while additional answers are still pending

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

## Q&A Coordinator — Lifecycle

The Q&A coordinator is a **dedicated per-session coroutine**, launched by the server when
the first question arrives for a session. It runs entirely outside the executor's coroutine
scope.

| Aspect | Behavior |
|--------|----------|
| **Scope** | Scoped to the session. If the session/executor terminates for any reason (noActivityTimeout, `/fail-workflow`, harness restart), the Q&A coroutine is **cancelled silently** — no answer delivered, no error. |
| **Concurrency** | One coordinator per session. A second `/user-question` does not launch a second coordinator — the server forwards the question to the existing coordinator, which appends to its internal queue and processes it when it reaches that item. |
| **Sequential processing** | Questions are presented to the human one at a time, in arrival order. The coordinator awaits the answer for question N before prompting for question N+1. |
| **Batch delivery** | Only after `answers.size == questions.size` (all queued questions answered) does the coordinator deliver. If a new question arrives while the coordinator is collecting answers, the queue grows and delivery waits until the new question is also answered. |
| **Post-delivery** | After `AckedPayloadSender` confirms delivery (ACK received), the coordinator sets `SessionEntry.isQAPending = false`. |

### noActivityTimeout During Q&A

Because pings are suppressed during Q&A, the executor should **NOT** fire `noActivityTimeout`
while `isQAPending` is true. The agent is known-idle; the only "crash" detectable is session
death (which the harness cannot distinguish from a healthy idle agent without pinging).

If the TMUX session dies during Q&A, the coordinator will eventually fail when it attempts
to deliver via `AckedPayloadSender` (send-keys fails or ACK never arrives) — the coordinator
handles this as a session-dead scenario and cleans up. No special handling needed from the
executor side.

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
`LlmUserQuestionHandler` or `TimeoutWithFallbackHandler` (see V2+ table below) is the right
choice — they are explicitly designed for autonomous operation.

### WHY Ping Suppression is Safe During Stdin Q&A

With `StdinUserQuestionHandler`, the human may be away for hours. Without ping suppression, the
health loop would fire pings every 30 minutes — each ping adds a TMUX `send-keys` message to
the agent's context window, which is pure waste: the agent is idle, waiting for the Q&A answer.
Over a multi-hour absence, this could accumulate dozens of pings, consuming context window
capacity that the agent needs for actual work.

With ping suppression (`isQAPending` gate), the executor knows the agent is in a known-idle
state — no pings needed, no context waste. The agent's context window is preserved for the
actual answer and subsequent work.

---

## Future Strategies (V2+)

| Strategy | Description |
|----------|-------------|
| `StdinUserQuestionHandler` | V1 — human at terminal |
| `LlmUserQuestionHandler` | Route to a `DirectLLM` (ref.ap.hnbdrLkRtNSDFArDFd9I2.E) configured with an expensive model for autonomous answers |
| `SlackUserQuestionHandler` | Post to Slack channel, wait for reply |
| `TimeoutWithFallbackHandler` | Wait N minutes for human, fall back to LLM |

---

## Checks Gated on `isQAPending`

The following executor health-aware await loop behaviors are **suppressed** when
`SessionEntry.isQAPending` is true:

| Check | Normal behavior | When Q&A pending |
|-------|----------------|-----------------|
| Health ping firing | Fire when `lastActivityTimestamp` stale > `normalActivity` | **Skip** — agent is known-idle awaiting TMUX answer |
| noActivityTimeout | Declare crash when stale > `normalActivity` + no ping response | **Skip** — agent is known-idle, not crashed |

These gates are documented in the health-aware await loop pseudocode
(ref.ap.QCjutDexa2UBDaKB3jTcF.E) and HealthMonitoring spec (ref.ap.RJWVLgUGjO5zAwupNLhA0.E).
