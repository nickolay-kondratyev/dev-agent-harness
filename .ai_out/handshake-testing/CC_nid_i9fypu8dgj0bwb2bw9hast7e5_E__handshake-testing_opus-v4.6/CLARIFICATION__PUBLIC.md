# Clarification: Handshake Testing

## Requirements (Clear, No Ambiguity)

1. **Fix handshake startup**: Agent spawned in tmux doesn't complete handshake because:
   - `TICKET_SHEPHERD_SERVER_PORT` not exported
   - `callback_shepherd.signal.sh` not on PATH
   - Bootstrap message doesn't instruct agent to call `started`

2. **Follow-up ticket**: Create ticket for end-to-end test with `straightforward` workflow.

## Design Decisions (Resolved)

- Add `serverPort` and `callbackScriptsDir` to `ClaudeCodeAdapter` constructor (adapter-level config, same for all sessions)
- Extract callback scripts from classpath resources at runtime for portability
- Update bootstrap message to tell agent to call `callback_shepherd.signal.sh started` immediately
