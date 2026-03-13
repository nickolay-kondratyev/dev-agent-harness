# UserQuestionHandler / ap.NE4puAzULta4xlOLh5kfD.E

Strategy interface for handling user questions from agents. Decouples the question-answering
mechanism from the server and protocol — the server calls the handler, the handler returns
the answer.

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

## Flow

1. Agent calls `callback_shepherd.signal.sh user-question "How should I handle X?"`
2. Script POSTs to `/callback-shepherd/signal/user-question` with `handshakeGuid` + question text
3. Server returns **200 immediately** — no blocking
4. Server updates `SessionEntry.lastActivityTimestamp` (resets health timer)
5. Server performs **deduplication check** (ref.ap.Girgb4gaq2aecYTHjUj8a.E — see [Question Deduplication](#question-deduplication--apgirgb4gaq2aecythjuj8ae) below).
   If the question is already pending for this session, skip to step 5a; otherwise proceed to step 6.
   - **5a.** Duplicate detected — server does **not** invoke the handler again. The duplicate
     request shares the same in-flight answer. When the original answer is delivered (step 9),
     the duplicate is satisfied implicitly. No additional TMUX delivery occurs.
6. **Agent waits** for the answer to arrive via TMUX (does not proceed)
7. Server looks up `SessionEntry` for context, then delegates to `UserQuestionHandler`
8. Handler obtains the answer (V1: stdin prompt; future: expensive LLM, Slack, etc.)
9. Harness writes answer to `comm/in/` in the sub-part's `.ai_out/` directory (e.g., `comm/in/qa_answer.md`)
10. Harness delivers the answer file path to the agent via `AckedPayloadSender`
   (ref.ap.tbtBcVN2iCl1xfHJthllP.E) — the file path is wrapped in the Payload Delivery ACK
   XML (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E), sent via TMUX `send-keys`, and the harness awaits
   the agent's `ack-payload` callback before considering the answer delivered
11. Agent reads the XML wrapper, ACKs the payload, then reads the answer file and continues

**No long-lived HTTP connections.** The answer is delivered via TMUX, not via the HTTP response.
This follows the core principle: HTTP = agent→harness signaling, TMUX = harness→agent delivery.
Delivery confirmation follows the same protocol as all other `send-keys` payloads — see
[Payload Delivery ACK Protocol](agent-to-server-communication-protocol.md#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe)
(ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).

---

## Question Deduplication / ap.Girgb4gaq2aecYTHjUj8a.E

If an agent sends the same `user-question` twice (curl retry, agent confusion, context-window
compaction replay), the harness must **not** prompt the human twice. The server maintains a
per-session pending-question queue that deduplicates by exact question text.

### Dedup Key

`(handshakeGuid, question)` — same session, same question text. Two different agents (different
HandshakeGuids) asking the same question are **not** deduplicated — each gets its own prompt.

### Mechanism

The server keeps a `pendingQuestions` map on the `SessionEntry`:

```
pendingQuestions: MutableMap<String, CompletableDeferred<String>>
```

- **Key**: the question text (exact string match)
- **Value**: a `CompletableDeferred<String>` that resolves to the answer

#### On incoming `/callback-shepherd/signal/user-question`:

1. Look up `SessionEntry` by `handshakeGuid`
2. Check `pendingQuestions[question]`
   - **Already present** → duplicate. Return **200 OK**. Do not invoke `UserQuestionHandler`.
     The in-flight deferred will deliver the answer when ready.
   - **Not present** → new question. Create a `CompletableDeferred<String>`, insert into
     `pendingQuestions[question]`, invoke `UserQuestionHandler` in a coroutine. Return **200 OK**.
3. When `UserQuestionHandler` returns the answer:
   - Complete the deferred: `pendingQuestions[question]!!.complete(answer)`
   - Remove the entry: `pendingQuestions.remove(question)`
   - Proceed with answer file write + `AckedPayloadSender` delivery (flow steps 9–11)

### Properties

| Property | Guarantee |
|----------|-----------|
| HTTP response | Always **200** — both original and duplicate |
| Human prompt | Shown **exactly once** per distinct question per session |
| Answer delivery | **One** TMUX delivery per question (the original flow) — duplicates piggyback |
| Cleanup | Entry removed from `pendingQuestions` after answer is obtained |
| Thread safety | `pendingQuestions` access guarded by the same `Mutex` backing `SessionsState` |

### Edge Cases

| Case | Behavior |
|------|----------|
| Same agent, same question, sent twice quickly | Second request gets 200, no duplicate prompt |
| Same agent, different question | Each question handled independently |
| Same question text from different agents | Each agent has its own `SessionEntry` — no cross-session dedup |
| Question answered, then same question sent again | `pendingQuestions` entry already removed → treated as new question (fresh prompt) |

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
| Timeout | None — blocks indefinitely until human responds |
| Context shown | Part name, sub-part name, sub-part role, HandshakeGuid |
| If human is away | Blocks forever. Acceptable for V1 (attended-only). |

---

## Future Strategies (V2+)

| Strategy | Description |
|----------|-------------|
| `StdinUserQuestionHandler` | V1 — human at terminal |
| `LlmUserQuestionHandler` | Route to expensive model (e.g., `DirectBudgetHighLLM` ref.ap.hnbdrLkRtNSDFArDFd9I2.E) for autonomous answers |
| `SlackUserQuestionHandler` | Post to Slack channel, wait for reply |
| `TimeoutWithFallbackHandler` | Wait N minutes for human, fall back to LLM |
