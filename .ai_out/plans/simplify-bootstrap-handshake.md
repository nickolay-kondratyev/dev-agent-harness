# Plan: Simplify Bootstrap Handshake — Initial Prompt Instead of `send-keys`

## Approach

Replace the two-step bootstrap (start agent → send bootstrap via TMUX `send-keys`) with a
single-step approach: **embed the bootstrap message as an initial prompt argument** in the
CLI command that starts the agent. This eliminates the timing guesswork between "TMUX session
created" and "agent ready to receive `send-keys`."

The rest of the protocol stays the same: Phase 2 (full instructions) is still delivered via
`send-keys` after `/signal/started` is received. All subsequent harness→agent communication
(pings, Q&A answers, iteration instructions) continues to use `send-keys`.

### Why This Is Better

| Before | After |
|--------|-------|
| Start agent with no input → guess when ready → `send-keys` bootstrap | Start agent WITH bootstrap as initial prompt |
| Race condition risk: `send-keys` sent before agent's input loop is ready | No race: bootstrap is agent's first input, atomically |
| Harness must manage the gap between "session created" and "bootstrap delivered" | Gap does not exist |
| When `/started` arrives, we know the agent is alive but don't know if it's ready for more `send-keys` | When `/started` arrives, we know the agent is alive AND has proven it can process input — safe for subsequent `send-keys` |

### What Does NOT Change

- Phase 2 (`send-keys` for instructions, pings, Q&A, iteration feedback) — unchanged
- HandshakeGuid env var export — unchanged (still in the shell command prefix)
- Callback scripts — unchanged
- Health monitoring — unchanged (minor wording only)
- `AgentSessionIdResolver` timing — unchanged (runs after `/started`)
- Server endpoints and payloads — unchanged
- Resume uses the same mechanism (confirmed: `claude --resume <id> "message"` works)

---

## Decision: Already Resolved

**Bootstrap delivery mechanism** — embed as initial prompt argument instead of `send-keys`.
- Both fresh start (`claude ... "<bootstrap>"`) and resume (`claude --resume <id> "<bootstrap>"`) support this.
- This was the user's proposal; validated and accepted.

---

## Requirements

### R1: `AgentStarter` interface accepts bootstrap message

The `buildStartCommand()` method (ref.ap.RK7bWx3vN8qLfYtJ5dZmQ.E) must accept the bootstrap
message so each agent type can embed it in its own way.

**Current** (`app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/AgentStarter.kt`):
```kotlin
fun buildStartCommand(): TmuxStartCommand
```

**New**:
```kotlin
fun buildStartCommand(bootstrapMessage: String): TmuxStartCommand
```

**Why on the interface, not appended externally**: Different agent types may embed the initial
prompt differently (positional arg, `-m` flag, stdin pipe, etc.). The `AgentStarter`
implementation knows its agent's CLI contract.

- Verifiable: `AgentStarter.buildStartCommand` signature requires `bootstrapMessage` parameter.

### R2: `ClaudeCodeAgentStarter` appends bootstrap as positional argument

The implementation (`app/src/main/kotlin/com/glassthought/shepherd/core/agent/starter/impl/ClaudeCodeAgentStarter.kt`)
appends the bootstrap message as a quoted positional argument to the `claude` command.

Example output:
```bash
bash -c 'cd /work && unset CLAUDECODE && export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && claude --model sonnet --system-prompt-file /path --dangerously-skip-permissions "Your GUID is handshake.xxx. Call callback_shepherd.signal.sh started now."'
```

- Verifiable: Unit test asserts `buildStartCommand(bootstrapMessage)` output contains the message as a quoted trailing argument to `claude`.

### R3: `SpawnTmuxAgentSessionUseCase` no longer sends bootstrap via `send-keys`

The spawn flow collapses steps 4 and 5 (current spec) into one step: create the TMUX session
with the start command that already contains the bootstrap. The separate `tmux send-keys`
call for bootstrap is removed.

- Verifiable: No `send-keys` call between session creation and the `/started` await.

### R4: Spec docs updated — bootstrap delivery mechanism

All spec documents that reference "bootstrap via TMUX `send-keys`" are updated to reflect
"bootstrap via initial prompt argument." See [Doc Touch Points](#doc-touch-points) for the
full list.

- Verifiable: `grep -r "send.keys.*bootstrap\|bootstrap.*send.keys" doc/` returns zero results
  after the update.

### R5: Hard constraint clarification updated

The hard constraint in `high-level.md` currently says:
> "The bootstrap message is delivered via TMUX `send-keys`, not CLI args."

This must be updated to reflect the opposite: the bootstrap IS delivered via CLI args (initial
prompt), while Phase 2 instructions continue via `send-keys`.

- Verifiable: Hard constraint text accurately describes the new protocol.

### R6: `send-keys` remains the channel for Phase 2 and beyond

TMUX `send-keys` is still the **only** harness→agent channel for post-bootstrap communication.
Nothing changes about Phase 2. All existing references to `send-keys` for instructions, pings,
Q&A answers, and iteration feedback remain accurate and must NOT be removed.

- Verifiable: Phase 2 flow steps in all docs still reference `send-keys`.

---

## Doc Touch Points

### Primary (substantial changes)

1. **`doc/use-case/SpawnTmuxAgentSessionUseCase.md`**
   - Section "Bootstrap Message — TMUX `send-keys`" → rename to "Bootstrap Message — Initial Prompt Argument"
   - Rewrite section body: bootstrap is part of the CLI command, not a separate `send-keys`
   - Update example commands: add bootstrap message as trailing arg
   - Remove the `tmux send-keys -t <pane_target> "<bootstrap_message>" Enter` example
   - Phase 1 steps 4-5 collapse into step 4 (session created WITH bootstrap)
   - Resume flow steps 3-4 collapse similarly
   - `ClaudeCodeAgentStarter` V1 description: remove "bootstrap delivered via TMUX `send-keys`"
   - "Resolved: Agent Startup Delay" section: update wording

2. **`doc/core/agent-to-server-communication-protocol.md`**
   - HandshakeGuid section line 68: "included in the bootstrap message (sent via TMUX `send-keys` after interactive start)" → "included in the bootstrap message (delivered as initial prompt argument)"
   - How the Callback Scripts Know the GUID section: remove "Then bootstrap message sent via TMUX send-keys" comment from example
   - Agent Startup Acknowledgment section (ap.xVsVi2TgoOJ2eubmoABIC.E): update contract, two-phase flow diagram, Claude Code example
   - Harness → Agent Communication section: update bullet describing bootstrap delivery

### Secondary (wording/table updates)

3. **`doc/high-level.md`**
   - Hard Constraints paragraph: update bootstrap delivery description
   - Key Technology Decisions table, "Agent start command" row
   - Key Technology Decisions table, "Startup acknowledgment" row

### Tertiary (no change needed — already correct)

4. **`doc/use-case/HealthMonitoring.md`** — references `send-keys` only for pings and iteration, not for bootstrap. No change.
5. **`doc/core/PartExecutor.md`** — references `send-keys` for instructions and pings. No change.
6. **`doc/core/ContextForAgentProvider.md`** — references `send-keys` for instruction file delivery. No change.
7. **`doc/core/UserQuestionHandler.md`** — references `send-keys` for Q&A answer delivery. No change.
8. **`doc/core/SessionsState.md`** — references `send-keys` for Q&A answer delivery. No change.

---

## Gates

### Gate 1: Spec docs updated

**Completed**: All doc touch points from [Primary] and [Secondary] are updated.
**Verify**: `grep -ri "send.keys.*bootstrap\|bootstrap.*send.keys" doc/` returns zero results.
Cross-check: Phase 2 `send-keys` references are preserved (spot-check 3 docs).
**Decision**: Proceed to code changes.

### Gate 2: `AgentStarter` interface + `ClaudeCodeAgentStarter` updated

**Completed**: Interface signature changed, implementation appends bootstrap as positional arg.
**Verify**: Existing unit tests pass (updated for new signature). New test: `buildStartCommand`
output includes the bootstrap message as a trailing quoted argument.
**Decision**: Proceed to spawn use case update.

### Gate 3: Spawn flow updated (if code exists)

**Completed**: `SpawnTmuxAgentSessionUseCase` passes bootstrap message to `AgentStarter` instead
of calling `send-keys` for bootstrap.
**Verify**: Unit tests for spawn flow verify no bootstrap `send-keys`. Integration test (if
applicable) validates end-to-end bootstrap + `/started` handshake.

---

## Risks

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Bootstrap message contains characters that break shell quoting when embedded as positional arg | Medium | `ClaudeCodeAgentStarter` already has `escapeForShell`. Ensure bootstrap content is simple (GUID + one instruction). Test with edge cases (quotes, newlines). |
| Future agent types might not support initial prompt arguments | Low | `AgentStarter` interface makes this each implementation's problem. A future agent type could fall back to a different mechanism. |
| Existing code references the old two-step pattern | Low | Gate 2 and 3 cover this. `grep` verification catches stragglers. |

---

## Open Questions

None — all decisions resolved.
