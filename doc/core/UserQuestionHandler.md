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

1. Agent calls `callback_shepherd.user-question.sh "How should I handle X?"`
2. Script POSTs to `/callback-shepherd/user-question` with `handshakeGuid` + question text
3. Server returns **200 immediately** — no blocking
4. Server updates `SessionEntry.lastActivityTimestamp` (resets health timer)
5. **Agent waits** for the answer to arrive via TMUX (does not proceed)
6. Server looks up `SessionEntry` for context, then delegates to `UserQuestionHandler`
7. Handler obtains the answer (V1: stdin prompt; future: expensive LLM, Slack, etc.)
8. Harness writes answer to temp file (`$HOME/.shepherd_agent_harness/tmp/agent_comm/`)
9. Harness sends file path to agent via TMUX `send-keys`
10. Agent reads temp file, continues

**No long-lived HTTP connections.** The answer is delivered via TMUX, not via the HTTP response.
This follows the core principle: HTTP = agent→harness signaling, TMUX = harness→agent delivery.

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
