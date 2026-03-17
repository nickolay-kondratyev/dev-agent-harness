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
5. **Agent waits** for the answer to arrive via TMUX (does not proceed)
6. Server looks up `SessionEntry` for context, then delegates to `UserQuestionHandler`
7. Handler obtains the answer (V1: stdin prompt; future: expensive LLM, Slack, etc.)
8. Harness writes answer to `comm/in/` in the sub-part's `.ai_out/` directory (e.g., `comm/in/qa_answer.md`)
9. Harness delivers the answer file path to the agent via `AckedPayloadSender`
   (ref.ap.tbtBcVN2iCl1xfHJthllP.E) — the file path is wrapped in the Payload Delivery ACK
   XML (ref.ap.r0us6iYsIRzrqHA5MVO0Q.E), sent via TMUX `send-keys`, and the harness awaits
   the agent's `ack-payload` callback before considering the answer delivered
10. Agent reads the XML wrapper, ACKs the payload, then reads the answer file and continues

**No long-lived HTTP connections.** The answer is delivered via TMUX, not via the HTTP response.
This follows the core principle: HTTP = agent→harness signaling, TMUX = harness→agent delivery.
Delivery confirmation follows the same protocol as all other `send-keys` payloads — see
[Payload Delivery ACK Protocol](agent-to-server-communication-protocol.md#payload-delivery-ack-protocol--apr0us6iysirrzrqha5mvo0qe)
(ref.ap.r0us6iYsIRzrqHA5MVO0Q.E).

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

---

## Future Strategies (V2+)

| Strategy | Description |
|----------|-------------|
| `StdinUserQuestionHandler` | V1 — human at terminal |
| `LlmUserQuestionHandler` | Route to expensive model (e.g., `DirectBudgetHighLLM` ref.ap.hnbdrLkRtNSDFArDFd9I2.E) for autonomous answers |
| `SlackUserQuestionHandler` | Post to Slack channel, wait for reply |
| `TimeoutWithFallbackHandler` | Wait N minutes for human, fall back to LLM |
