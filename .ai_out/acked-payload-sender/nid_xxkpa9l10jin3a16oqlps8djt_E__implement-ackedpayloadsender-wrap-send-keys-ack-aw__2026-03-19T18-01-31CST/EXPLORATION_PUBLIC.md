# Exploration: AckedPayloadSender Dependencies

## All Dependencies Confirmed ✓

| Dependency | File | Status |
|---|---|---|
| PayloadId value class | `app/src/main/kotlin/com/glassthought/shepherd/core/server/PayloadId.kt` | ✓ Exists, tested |
| SessionEntry (with `pendingPayloadAck: PayloadId?`) | `app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt` | ✓ Exists |
| TmuxCommunicator.sendKeys() | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxCommunicator.kt` | ✓ Exists |
| TmuxAgentSession | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/TmuxAgentSession.kt` | ✓ Exists |
| HandshakeGuid | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt` | ✓ Exists |
| ProtocolVocabulary.PAYLOAD_ACK_TAG | `app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt` | ✓ Exists |
| ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT | same file | ✓ Exists |
| ProtocolVocabulary.Signal.ACK_PAYLOAD | same file | ✓ Exists |

## Key Interfaces

### TmuxCommunicator.sendKeys(paneTarget: String, text: String)
- Sends text literally with `-l` flag, then sends Enter separately
- Used for all harness→agent payload delivery

### TmuxAgentSession
- `data class TmuxAgentSession(val tmuxSession: TmuxSession, val resumableAgentSessionId: ResumableAgentSessionId)`
- `tmuxSession.sendKeys(text)` delegates to communicator

### SessionEntry
- Has `pendingPayloadAck: PayloadId?` field
- Is a `data class` — updating requires `.copy()`
- Field set before send, cleared by server on matching ACK callback

### PayloadId.generate(handshakeGuid, counter)
- Format: `{first-8-chars-of-guid-uuid}-{counter}` e.g. `a1b2c3d4-3`

## XML Wrapping Format (EXACT)
```xml
<payload_from_shepherd_must_ack payload_id="a1b2c3d4-3" MUST_ACK_BEFORE_PROCEEDING="callback_shepherd.signal.sh ack-payload a1b2c3d4-3">
[payloadContent]
</payload_from_shepherd_must_ack>
```

## Retry Policy
- 3 attempts, 3 min each, same PayloadId
- Throw on exhaustion → caller treats as AgentCrashed

## Key Design Points
- SessionEntry is a data class — need a mechanism to update `pendingPayloadAck` (likely through SessionsState registry)
- Need to understand how SessionEntry is mutated (it's immutable data class, so there must be a mutable holder)
- AckedPayloadSender needs access to: TmuxCommunicator (via TmuxAgentSession), SessionEntry mutation mechanism, OutFactory
