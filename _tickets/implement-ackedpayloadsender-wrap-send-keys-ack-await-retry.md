---
id: nid_xxkpa9l10jin3a16oqlps8djt_E
title: "Implement AckedPayloadSender — wrap, send-keys, ACK-await, retry"
status: in_progress
deps: [nid_ejfyrux3m22ww1yl9smu57wwz_E, nid_5o5wyxuzoz7qrkuq4wuo2gnjr_E]
links: []
created_iso: 2026-03-19T00:40:18Z
status_updated_iso: 2026-03-19T18:01:27Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, protocol]
---

## Context

Spec: `doc/core/agent-to-server-communication-protocol.md` (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E), section "Shared Abstraction — AckedPayloadSender" (ref.ap.tbtBcVN2iCl1xfHJthllP.E)

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt`

### Interface

```kotlin
interface AckedPayloadSender {
    /**
     * Wrap [payloadContent] in the Payload Delivery ACK XML, send via TMUX send-keys,
     * and await ACK. Retries per the retry policy (3 attempts, 3 min each).
     *
     * Returns on successful ACK. Throws on all-retries-exhausted (caller handles as crash).
     */
    suspend fun sendAndAwaitAck(
        tmuxSession: TmuxAgentSession,
        sessionEntry: SessionEntry,
        payloadContent: String,
    )
}
```

### Implementation (AckedPayloadSenderImpl)

1. **Generate PayloadId** using HandshakeGuid from session + per-session AtomicInteger counter
2. **Wrap payload** in XML:
```xml
<payload_from_shepherd_must_ack payload_id="a1b2c3d4-3" MUST_ACK_BEFORE_PROCEEDING="callback_shepherd.signal.sh ack-payload a1b2c3d4-3">
[payloadContent]
</payload_from_shepherd_must_ack>
```
3. **Set `pendingPayloadAck`** on SessionEntry before sending
4. **Send via TmuxCommunicator.sendKeys()** (literal mode)
5. **Poll `pendingPayloadAck`** on SessionEntry until null (ACK received) or timeout
6. **Retry on timeout** — same PayloadId, same content

### Retry Policy
| Attempt | Action | On no ACK |
|---------|--------|----------|
| 1 (initial send) | Send wrapped payload | Wait 3 minutes |
| 2 (first retry) | Re-send same payload | Wait 3 minutes |
| 3 (second retry) | Re-send same payload | All retries exhausted |

After all retries exhausted (9 min total): throw exception. Caller treats as AgentCrashed.

### Callers (all use this single abstraction)
- AgentFacadeImpl: Phase 2 work instructions after bootstrap
- AgentFacadeImpl: iteration feedback to doer/reviewer
- Executor Q&A answer batch delivery
- AgentFacadeImpl: health pings

### Scope — What Gets Wrapped
| Content type | Wrapped? |
|---|---|
| Work instructions (Phase 2 file pointer) | Yes |
| User-question answers | Yes |
| Iteration feedback instructions | Yes |
| PUBLIC.md re-instruction | Yes |
| Health pings | Yes |
| Bootstrap messages | NO (delivered as initial prompt args, /started is the ACK) |

## Dependencies
- `PayloadId` value class (nid_ejfyrux3m22ww1yl9smu57wwz_E)
- `SessionEntry` with `pendingPayloadAck` field (nid_erd0khe8sg0vqbnwtg23aqzw9_E)
- `TmuxCommunicator` (already exists at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxCommunicator.kt`)
- `ProtocolVocabulary.PAYLOAD_ACK_TAG` (already exists)

## Testing
- Unit test with fake TmuxCommunicator + manual SessionEntry: ACK arrives immediately -> returns
- Unit test: ACK timeout on first attempt, arrives on retry -> returns
- Unit test: all retries exhausted -> throws
- Unit test: XML wrapping format matches spec exactly
- Unit test: PayloadId is set on SessionEntry before send-keys

