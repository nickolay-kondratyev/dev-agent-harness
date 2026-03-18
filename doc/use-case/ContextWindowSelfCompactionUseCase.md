# Context Window Self-Compaction — UseCase / ap.8nwz2AHf503xwq8fKuLcl.E

Detects context window exhaustion in TMUX-powered agents and performs controlled
self-compaction at **done boundaries** — killing the old session and spawning a fresh one
with a `PRIVATE.md` context summary.

**V1 approach:** Claude Code's built-in auto-compaction remains **enabled** for emergency
mid-task compaction. The harness performs **controlled self-compaction only at done
boundaries** (soft threshold). This eliminates the complex Ctrl+C emergency interrupt path.
See [`doc_v2/our-own-emergency-compression.md`](../../doc_v2/our-own-emergency-compression.md)
for the deferred V2 harness-controlled emergency compression design.

**Scope: all TMUX-powered sub-parts** — planning (PLANNER, PLAN_REVIEWER) and execution
(doers and reviewers). The logic lives in `PartExecutor` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E),
which is shared across all part types. No sub-part is exempt.

All agent operations in this use case (send compaction instructions, read context window state,
kill sessions, spawn new sessions) flow through the `AgentFacade` facade
(ref.ap.9h0KS4EOK5yumssRCJdbq.E). `PartExecutor` never accesses `SessionsState`,
`TmuxCommunicator`, or `TmuxSessionManager` directly. This enables unit testing of the full
compaction state machine via `FakeAgentFacade` + virtual time.

---

## Why This Exists

After many doer↔reviewer iterations, the agent's context fills up. Claude Code compacts
automatically, but its automatic compaction happens at unpredictable times and the
summarization is not guided. The agent may degrade silently — missing instructions,
producing garbage, or looping. The existing health monitor
(ref.ap.6HIM68gd4kb8D2WmvQDUK.E) only checks "alive," not "functional."

**Solution:** Monitor context window state via an external hook that writes
`context_window_slim.json`. At done boundaries, when the context is running low, the
harness asks the agent to summarize its context into `PRIVATE.md`, kills the session,
and spawns a fresh one. The new session receives `PRIVATE.md` as part of its instructions —
a clean context window with compressed but complete prior knowledge.

**Emergency fallback:** If the agent exhausts its context between done boundaries, Claude
Code's native auto-compaction handles it. This is less controlled but adequate for V1 —
the granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) creates frequent done
boundaries, making mid-task exhaustion rare.

---

## Vocabulary

| Term | Definition |
|------|------------|
| **Self-compaction** | Harness-controlled process: agent summarizes context → writes `PRIVATE.md` → signals `self-compacted` → harness kills session → spawns fresh session with `PRIVATE.md`. |
| **context_window_slim.json** | External hook artifact at `${HOME}/.vintrin_env/claude_code/session/<SessionID>/context_window_slim.json`. Written by a hook outside Shepherd after every conversation turn (when the agent stops thinking). Format: `{"file_updated_timestamp": "<ISO-8601 UTC>", "remaining_percentage": N}` where N is 0–100 (100 = fresh, 0 = exhausted). The `file_updated_timestamp` field is used for staleness detection — if the timestamp is older than `contextFileStaleTimeout`, the value is treated as stale (unknown). |
| **Soft threshold** | `remaining_percentage ≤ SELF_COMPACTION_SOFT_THRESHOLD` (default: 35). Triggers when the agent has **used 65%** of its context (35% remaining). Checked at `done` boundaries — proactive compaction while the agent still has room to produce a quality summary. |
| **Session rotation** | Kill old TMUX session → spawn new one for the same sub-part. New HandshakeGuid, new session record in `sessionIds` array. |
| **PRIVATE.md** | Agent's self-compaction summary. Written to `${sub_part}/private/PRIVATE.md` in `.ai_out/`. Contains compressed but context-rich summary of the agent's work, decisions, and challenges. |

---

## Single Threshold — Done Boundary Only

V1 uses a single compaction trigger: **`DONE_BOUNDARY`** — checked after every
`AgentSignal.Done` (any result: `COMPLETED`, `PASS`, `NEEDS_ITERATION`).

### CompactionTrigger

```kotlin
enum class CompactionTrigger {
    /** Agent at done boundary with ≤35% context remaining. No interrupt needed. */
    DONE_BOUNDARY,
}
```

> **V2:** A `EMERGENCY_INTERRUPT` trigger with continuous 1-second polling and Ctrl+C
> interrupt is designed in
> [`doc_v2/our-own-emergency-compression.md`](../../doc_v2/our-own-emergency-compression.md).

### Trigger Detection

| Trigger | Where detected | Condition |
|---------|---------------|-----------|
| `DONE_BOUNDARY` | After `AgentSignal.Done` + PUBLIC.md validation (ref.ap.THDW9SHzs1x2JN9YP9OYU.E) | `remaining_percentage ≤ SELF_COMPACTION_SOFT_THRESHOLD` (default: 35) |

### Compaction Flow — `performCompaction(handle, trigger)`

```
performCompaction(handle, trigger: CompactionTrigger.DONE_BOUNDARY):
    │
    ├─ 1. Pre-compaction: no-op (agent already idle at done boundary)
    │
    ├─ 2. Core compaction:
    │   ├─ Send self-compaction instruction via AgentFacade.sendPayload(config, handle, payload)
    │   │   (creates fresh signal deferred implicitly — ref.ap.9h0KS4EOK5yumssRCJdbq.E)
    │   │   → update handle with returned value
    │   ├─ Await signal on handle.signal (with timeout: SELF_COMPACTION_TIMEOUT)
    │   │   ├─ SelfCompacted → proceed
    │   │   ├─ Done (first occurrence) → re-instruct ("you are in compaction mode — signal self-compacted") → await again
    │   │   └─ Done (second occurrence) → AgentCrashed ("agent cannot follow compaction protocol")
    │   ├─ Validate PRIVATE.md exists and is non-empty
    │   ├─ Git commit — captures PRIVATE.md + any changes before compaction
    │   └─ Kill session via AgentFacade.killSession(handle) → set handle = null
    │
    └─ 3. Post-compaction:
        └─ handle is now null. Continue normal flow (next sub-part, iteration
           restart, etc.). The next sendPayload call will transparently spawn
           a fresh session.
```

### Entry Point: Done Boundary (Soft Threshold)

```
Agent signals done
    │
    ├─ PUBLIC.md validation (existing step)
    │
    ├─ Read context_window_slim.json
    │   ├─ remaining_percentage > SELF_COMPACTION_SOFT_THRESHOLD → continue normal flow
    │   └─ remaining_percentage ≤ SELF_COMPACTION_SOFT_THRESHOLD:
    │       └─ performCompaction(sessionEntry, DONE_BOUNDARY)
    │
    └─ Continue normal flow (GitCommitStrategy, next sub-part, iteration restart)
```

The next time the executor needs this sub-part (e.g., doer on iteration > 1), it detects
no live session and spawns a new one via the standard spawn flow
(ref.ap.hZdTRho3gQwgIXxoUtTqy.E). The new session's instructions include `PRIVATE.md`
via `ContextForAgentProvider` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E).

---

## ContextWindowStateReader / ap.ufavF1Ztk6vm74dLAgANY.E

Agent-type-specific interface for reading context window state. Follows the same OCP
pattern as `AgentTypeAdapter` (ref.ap.A0L92SUzkG3gE0gX04ZnK.E) — one implementation per agent type.

```kotlin
interface ContextWindowStateReader {
    /**
     * Reads the current context window state for an agent session.
     * Throws [ContextWindowStateUnavailableException] if the state file
     * is not present — this is a hard stop failure indicating the
     * external hook is not configured.
     *
     * Returns [ContextWindowState] with [ContextWindowState.remainingPercentage] = null
     * when the file is present but its [ContextWindowState.fileUpdatedTimestamp] is older
     * than [HarnessTimeoutConfig.contextFileStaleTimeout].  Callers MUST treat null as
     * "unknown" — no compaction should be triggered, but a warning must be logged.
     */
    suspend fun read(agentSessionId: String): ContextWindowState

    /**
     * Validates that the context window state file exists for this session.
     * Called after session ID resolution to fail fast on hook misconfiguration.
     */
    suspend fun validatePresence(agentSessionId: String)
}

data class ContextWindowState(
    /**
     * Remaining context percentage (0–100, 100 = fresh, 0 = exhausted).
     * Null when the value is stale (file_updated_timestamp older than contextFileStaleTimeout).
     * Callers must not trigger compaction on a null value; they should log a warning instead.
     */
    val remainingPercentage: Int?,
)
```

### V1 Implementation: ClaudeCodeContextWindowStateReader

Reads from `${HOME}/.vintrin_env/claude_code/session/<agentSessionId>/context_window_slim.json`.

- File missing → `ContextWindowStateUnavailableException` (extends `AsgardBaseException`)
  — **hard stop failure**. Means the external hook is not writing context state.
- File present but malformed (missing `remaining_percentage` or `file_updated_timestamp`,
  or unparseable JSON) → same exception with parse error details.
- File present, `file_updated_timestamp` older than `contextFileStaleTimeout` (default 5 min):
  → return `ContextWindowState(remainingPercentage = null)`. Log a warning (not an exception
  — the hook may have temporarily stalled; health monitoring will catch a dead agent).

  <!-- WHY staleness is NOT a hard-stop: the hook stopping mid-session is a recoverable
       situation. An unknown context state is safe to ignore (no compaction triggered).
       A truly dead agent will be caught by the existing noActivityTimeout in health monitoring.
       Making it a hard-stop would cause false-positive AgentCrashed events on transient
       hook hiccups. -->

- `validatePresence()` called after `AgentTypeAdapter.resolveSessionId()` resolves the session ID
  (step 6a in spawn flow — ref.ap.hZdTRho3gQwgIXxoUtTqy.E). Confirms hook is active
  before any work begins.

### Caller Behavior on Stale State

The done-boundary compaction check must handle `remainingPercentage == null`:

```
contextState = agentFacade.readContextWindowState(handle)
if (contextState.remainingPercentage == null) {
    // Log warning: "context_window_slim.json is stale — skipping compaction check"
    // Do NOT trigger compaction. Continue normal flow.
    continue
}
if (contextState.remainingPercentage <= SELF_COMPACTION_SOFT_THRESHOLD) {
    // trigger compaction
}
```

This is the **safe default**: a stale hook freezes compaction decisions rather than
triggering false compactions or silently ignoring context exhaustion. The existing
health-monitoring timeout (`noActivityTimeout`) will catch an agent that has truly
run out of context and gone silent.

### Non-TMUX Agents

`--print` mode agents (via `NonInteractiveAgentRunner` — ref.ap.ad4vG4G2xMPiMHRreoYVr.E)
do not use context window monitoring. They run a single request/response — no iteration,
no context accumulation.

---

## Self-Compacted Signal / ap.HU6KB4uRDmOObD54gdjYs.E

New endpoint and `AgentSignal` variant for self-compaction completion.

### Endpoint

`/callback-shepherd/signal/self-compacted` — **lifecycle signal** (completes the deferred).

- Payload: `{ "handshakeGuid": "handshake.xxx" }` (same as other signals)
- Server behavior: lookup session → complete `signalDeferred` with `AgentSignal.SelfCompacted`
- Updates `lastActivityTimestamp` (ref.ap.igClEuLMC0bn7mDrK41jQ.E)

### AgentSignal Extension

```kotlin
sealed class AgentSignal {
    data class Done(val result: DoneResult) : AgentSignal()
    data class FailWorkflow(val reason: String) : AgentSignal()
    data class Crashed(val details: String) : AgentSignal()
    object SelfCompacted : AgentSignal()  // NEW
}
```

### Callback Script Usage

```bash
# After writing PRIVATE.md during self-compaction:
callback_shepherd.signal.sh self-compacted
```

This is included in the self-compaction instruction (not in the standard callback help
block, since agents don't call it spontaneously — only in response to a harness-initiated
compaction instruction).

### Strict Signal Enforcement During Compaction

During compaction, `AgentSignal.SelfCompacted` is the **only** valid success signal.
If the agent signals `done` instead:

1. **Re-instruct once:** "You are in compaction mode — write `PRIVATE.md` then signal
   `self-compacted`, not `done`." Await again within the remaining compaction timeout.
2. **If `done` received again:** `AgentCrashed("agent cannot follow compaction protocol")`.

**No PRIVATE.md existence check as a fallback.** The signal is the protocol contract —
producing the right artifact with the wrong signal is still a protocol violation. This approach:
- Keeps signal semantics unambiguous (`done` = work complete, `self-compacted` = compaction complete)
- Prevents protocol drift where agents learn they can signal `done` and have it silently accepted
- Produces an explicit failure (re-instruction, then crash) rather than hiding the violation

---

## Self-Compaction Instruction Message / ap.kY4yu9B3HGvN66RoDi0Fb.E

Sent to the agent via `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E) when
self-compaction is triggered at a done boundary.

### Template

```markdown
Your context window is running low. Summarize this chat into
`<absolute_path_to_private_md>` so work can continue in a new chat.

Preserve all context needed for a new chat to understand:
- What we're doing and why
- At which point we are in the work
- All challenges we've had and how we've solved them
- Key decisions made and why
- Any patterns or discoveries about the codebase

Make the summary as **concise** as possible but context rich.

After writing the file, signal completion:
`callback_shepherd.signal.sh self-compacted`
```

### Compaction Timeout

The agent has **5 minutes** to write `PRIVATE.md` and signal `self-compacted`.
Compaction is a lightweight summarization task — 5 minutes is generous.
If timeout expires → `PartResult.AgentCrashed("Agent failed to self-compact within timeout")`.

---

## PRIVATE.md — Directory Schema Change

### Updated Sub-Part Directory Structure

```
└── ${sub_part}/
    ├── private/
    │   └── PRIVATE.md          # Self-compaction summary (created by agent during compaction)
    └── comm/
        ├── in/
        │   └── instructions.md
        └── out/
            └── PUBLIC.md
```

This updates the `.ai_out/` directory schema (ref.ap.BXQlLDTec7cVVOrzXWfR7.E).

### Previous "No PRIVATE.md" Decision — Superseded

The ai-out directory schema previously stated "No PRIVATE.md" with rationale that an agent's
private state lives in its conversation history. Self-compaction introduces a legitimate
need for persisted private state: when the session is killed and respawned, the conversation
history is gone. `PRIVATE.md` bridges this gap.

### PRIVATE.md Lifecycle

| Event | What happens |
|-------|-------------|
| First run | `private/PRIVATE.md` does not exist. `ContextForAgentProvider` skips it. |
| Self-compaction triggered | Agent writes `PRIVATE.md`. Git commit captures it. |
| New session spawns | `ContextForAgentProvider` includes `PRIVATE.md` in instructions. |
| Second self-compaction | Agent receives old `PRIVATE.md` in instructions. Writes new `PRIVATE.md` that incorporates the old content. Old version preserved in git history. |
| Part completes | `PRIVATE.md` remains in `.ai_out/` as a historical artifact. |

### What Goes in PRIVATE.md vs PUBLIC.md

| PRIVATE.md | PUBLIC.md |
|---|---|
| Agent's own context continuation — what it was doing, where it was, what it learned | Agent work log — decisions, rationale, review verdicts |
| Challenges encountered and solutions found | What was implemented or reviewed |
| Internal reasoning and dead ends explored | Part-specific trade-offs |
| Only consumed by the same sub-part's next session | Consumed by other sub-parts and agents |

---

## ContextForAgentProvider Change / ref.ap.9HksYVzl1KkR9E1L2x8Tx.E

### New Entry in Instruction Concatenation

`PRIVATE.md` is included in instruction assembly for **all agent types** (execution, planner,
plan-reviewer) when the file exists.

**Position in concatenation order** — inserted between role definition and part context
(high priority, since it contains the agent's own prior knowledge):

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 1 | **Role definition** | `$TICKET_SHEPHERD_AGENTS_DIR` | Unchanged |
| **1b** | **PRIVATE.md (if exists)** | `${sub_part}/private/PRIVATE.md` | **NEW** — Self-compaction context from prior session. Only present after session rotation. |
| 2 | **Part context** | `current_state.json` | Unchanged |
| 3 | **Ticket** | CLI `--ticket` | Unchanged |
| ... | *(remaining sections unchanged)* | | |

**Inclusion rule:** If `${sub_part}/private/PRIVATE.md` exists and is non-empty, include it.
If it does not exist, skip silently (no error — most sub-parts will never self-compact).

---

## Health-Aware Await Loop — No Emergency Compaction Check (V1)

V1's health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E) does **NOT** poll context
window state continuously. Context window state is read **only at done boundaries**.

The loop structure remains focused on health monitoring (liveness detection via
`lastActivityTimestamp`). No 1-second context window polling. No emergency interrupt path.

> **V2:** Adds 1-second context window polling and emergency interrupt.
> See [`doc_v2/our-own-emergency-compression.md`](../../doc_v2/our-own-emergency-compression.md).

---

## context_window_slim.json Validation After Session ID Resolution

After `AgentTypeAdapter.resolveSessionId()` resolves the session ID (step 6a in spawn flow —
ref.ap.hZdTRho3gQwgIXxoUtTqy.E), `AgentFacadeImpl` calls
`contextWindowStateReader.validatePresence(agentSessionId)` internally as part of
the spawn step within `sendPayload()`.

**Timing:** This runs after `/signal/started` is received. At this point:
- The agent is alive and has processed the bootstrap message
- The session ID is resolved from JSONL artifacts
- The external hook should have written `context_window_slim.json`

**Failure:** If the file does not exist → `ContextWindowStateUnavailableException` →
`PartResult.AgentCrashed("context_window_slim.json not found — external hook not configured")`.

This catches hook misconfiguration early — within 3 minutes of spawn — rather than
discovering it later when we need to read the context state.

---

## Hard Constraint Modification / ref.ap.NAVMACFCbnE7L6Geutwyk.E (high-level.md)

> **One TMUX session per sub-part at a time.** A sub-part gets exactly one TMUX session,
> spawned on first run and kept alive across iteration loops. The session is killed when the
> **part** completes, or when **self-compaction** triggers session rotation
> (ref.ap.8nwz2AHf503xwq8fKuLcl.E). After session rotation, a new session is spawned for
> the same sub-part. No two sessions are alive simultaneously for the same sub-part.

---

## Impact on PartExecutor — Unified Session Lifecycle

`PartExecutorImpl` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) uses a single call for all
instruction delivery — first run, re-instruction, and post-compaction are identical:

```kotlin
handle = agentFacade.sendPayload(config, handle, instructions)
// handle is null on first run and after compaction kill
// AgentFacade transparently: spawns if null/dead, re-uses if alive
```

After session rotation (self-compaction `killSession()` sets `handle = null`), the next
`sendPayload` call transparently spawns a fresh session. No explicit session-existence
check in the executor.

All agent operations go through `AgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E). The
executor is agnostic to session lifecycle — it always calls one method regardless of
session state.

**DRY between planning and execution:** The `PartExecutor` is already shared between
planning (PLANNER↔PLAN_REVIEWER) and execution parts. The self-compaction logic lives in
the executor, so it applies to both without duplication.

---

## Session Rotation Detail

The compaction steps execute via `AgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E):

1. **Validate PRIVATE.md:** Check `${sub_part}/private/PRIVATE.md` exists and is non-empty.
   If missing after one retry → `PartResult.AgentCrashed`.
2. **Git commit:** `GitCommitStrategy.onSubPartDone` — captures PRIVATE.md + any other
   changes the agent made before compaction.
3. **Kill session:** `agentFacade.killSession(handle)` — internally kills TMUX session
   and cleans up `SessionsState`.

**Post-compaction:** `handle = null`. No immediate respawn. The next time the executor
needs this sub-part, it calls `agentFacade.sendPayload(config, null, instructions)` —
AgentFacade spawns a fresh session transparently (standard spawn flow —
ref.ap.hZdTRho3gQwgIXxoUtTqy.E).

---

## Interaction with Other Use Cases

### Granular Feedback Loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E)

The inner feedback loop creates **frequent done boundaries** — one after each feedback item,
and additional boundaries during per-item rejection negotiation. Each done boundary is a
natural soft-compaction checkpoint. The doer processes one item, hits a done boundary,
compacts if needed, then starts fresh on the next item.

**The inner feedback loop makes self-compaction proactive rather than reactive.** See
[`granular-feedback-loop.md`](../plan/granular-feedback-loop.md) for the full spec.

### FailedToConvergeUseCase (ref.ap.RJWVLgUGjO5zAwupNLhA0.E)

If self-compaction is triggered at a done boundary and the iteration budget is also exceeded,
self-compaction takes priority. The agent compacts first, then `FailedToConvergeUseCase`
runs with the compacted state. Rationale: the user decision (continue or abort) should
happen with a healthy agent, not an exhausted one.

### Idle Session Death

Self-compaction and idle-session-death both trigger transparent respawn via
`AgentFacade.sendPayload()`. They differ in intent:
- Self-compaction: intentional kill (context full) → fresh start with PRIVATE.md context
- Idle session death: unintentional kill (OOM, external) → fresh start with current instructions

Both use the same `sendPayload(config, null, instructions)` code path — DRY by design.
Neither uses `--resume` (see V2 Resume below for when `--resume` is appropriate).

### V2 Resume (ref.ap.LX1GCIjv6LgmM7AJFas20.E)

Self-compaction does NOT use `--resume`. The whole point is a fresh context window.
`--resume` preserves the conversation (and its context usage). These are complementary
strategies:
- Self-compaction: context is filling up → summarize and start fresh
- Resume: harness crashed → restore where we left off

---

## Thresholds — Configuration

### V1: Global Defaults

| Threshold | `HarnessTimeoutConfig` field | Default | Meaning |
|-----------|------------------------------|---------|---------|
| `SELF_COMPACTION_SOFT_THRESHOLD` | `contextWindowSoftThresholdPct` | 35 | Compact at done boundary when `remaining_percentage ≤ 35` (i.e., 65% of context used, 35% remaining) |
| `SELF_COMPACTION_TIMEOUT` | `selfCompactionTimeout` | 5 min | Max time for agent to write PRIVATE.md and signal |
| `contextFileStaleTimeout` | `HarnessTimeoutConfig` field | 5 min | Maximum age of `file_updated_timestamp` in `context_window_slim.json` before the value is considered stale. When stale: `remainingPercentage` is treated as null (unknown) — no compaction is triggered, a warning is logged. Uses `file_updated_timestamp` from the JSON body (not OS file mtime). |

**Centralized constants:** All threshold values are fields of `HarnessTimeoutConfig`
(`com.glassthought.shepherd.core.data.HarnessTimeoutConfig`) — never as magic numbers in the
codebase. Injected via `ShepherdContext.timeoutConfig`; tests use `HarnessTimeoutConfig.forTests()`
for fast timeouts. One place to tune, one place to review.

Configured via environment variables or harness config. Not per-sub-part in V1.

---

## Spec Documents Requiring Updates

| Document | Change |
|----------|--------|
| `doc/high-level.md` | Hard Constraint modification ("one session at a time"), add Context Window Monitoring section, link to this spec |
| `doc/core/PartExecutor.md` | Self-compaction step after done in executor flow |
| `doc/use-case/HealthMonitoring.md` | Reference compaction at done boundaries |
| `doc/core/ContextForAgentProvider.md` | PRIVATE.md in instruction concatenation (position 1b) |
| `doc/schema/ai-out-directory.md` | New `private/PRIVATE.md` in directory tree, supersede "No PRIVATE.md" section |
| `doc/core/agent-to-server-communication-protocol.md` | New `/signal/self-compacted` endpoint |
| `doc/use-case/SpawnTmuxAgentSessionUseCase.md` | context_window_slim.json validation after session ID resolution |
| `doc/core/SessionsState.md` | Clarification: multiple session records per sub-part after rotation |
| `doc/high-level.md` (Key Technology Decisions) | Context window monitoring entry |

---

## Requirements

### R1: ContextWindowStateReader Interface + ClaudeCode Implementation
- Interface with `read(agentSessionId)` and `validatePresence(agentSessionId)` methods
- `ClaudeCodeContextWindowStateReader` reads from `${HOME}/.vintrin_env/claude_code/session/<agentSessionId>/context_window_slim.json`
- File missing → `ContextWindowStateUnavailableException` (hard stop)
- File malformed (missing required fields or unparseable JSON) → same exception with parse error details
- File present, `file_updated_timestamp` older than `contextFileStaleTimeout` → `ContextWindowState(remainingPercentage = null)` + log warning (not an exception)
- `ContextWindowState.remainingPercentage` is nullable (`Int?`); null = stale/unknown
- Callers (done-boundary check): skip compaction trigger when `remainingPercentage == null`, log warning instead
- Verifiable: unit test with fake file (fresh timestamp → value returned); unit test (stale timestamp → null returned + warning logged); unit test (file missing → exception); integration test confirming file presence after real Claude Code session

### R2: context_window_slim.json Validation After Session ID Resolution
- After `AgentTypeAdapter.resolveSessionId()` resolves session ID, call `contextWindowStateReader.validatePresence()`
- Missing file → `PartResult.AgentCrashed` with clear error about hook misconfiguration
- Verifiable: unit test — mock resolver returns ID, mock reader throws → executor returns AgentCrashed

### R3: `performCompaction()` — Done-Boundary Compaction Flow
Single method for `DONE_BOUNDARY` trigger:
- No pre-compaction interrupt
- Core steps: send instruction → await `SelfCompacted` → validate PRIVATE.md → git commit → kill session
- Post-compaction = lazy respawn (handle = null)
- Verifiable: unit test — done → low context → compaction instruction → SelfCompacted → session killed

### R4: Trigger Detection — Done Boundary
- After `AgentSignal.Done` + PUBLIC.md validation, read context window state
- If `remaining_percentage ≤ SELF_COMPACTION_SOFT_THRESHOLD` (default 35) → call `performCompaction(DONE_BOUNDARY)`
- Verifiable: unit test — done signal → low context → compaction → session killed; done signal → healthy context → no compaction

### R5: Self-Compacted Signal Endpoint
- New endpoint `/callback-shepherd/signal/self-compacted`
- New `AgentSignal.SelfCompacted` variant
- Server completes `signalDeferred` with `SelfCompacted`
- Updates `lastActivityTimestamp`
- Callback script: `callback_shepherd.signal.sh self-compacted`
- Verifiable: unit test — server receives self-compacted → deferred completed correctly

### R6: PRIVATE.md in .ai_out/ Directory Structure
- New path: `${sub_part}/private/PRIVATE.md`
- Created by agent during self-compaction
- Overwritten on subsequent self-compactions (git preserves history)
- `AiOutputStructure` creates `private/` directory alongside `comm/`
- Verifiable: directory creation test

### R7: ContextForAgentProvider Includes PRIVATE.md
- Check for `private/PRIVATE.md` in sub-part directory
- If exists and non-empty → include in instruction assembly at position 1b (after role def, before part context)
- If not exists → skip silently (no error)
- Applies to all instruction assembly methods (execution, planner, plan-reviewer)
- Verifiable: unit test — instructions with/without PRIVATE.md present

### R8: Session Rotation
- After self-compaction: `agentFacade.killSession()` (internally kills TMUX, cleans
  SessionsState), set `handle = null`
- Next `agentFacade.sendPayload(config, null, instructions)` transparently spawns fresh session
- New HandshakeGuid, new session record in `sessionIds` array
- PRIVATE.md picked up by ContextForAgentProvider in new instructions
- Works for both planning and execution parts (DRY via shared PartExecutor)
- Verifiable: unit test via `FakeAgentFacade` — full rotation sequence;
  integration test — real session rotation

### R9: Self-Compaction Instruction Message
- Template includes: PRIVATE.md path, summarization guidelines, signal instruction
- Verifiable: template renders correct paths and signal commands

---

## Implementation Gates

### Gate 1: Foundation — Reader + Validation
**Scope:** R1, R2
**What:** ContextWindowStateReader interface + ClaudeCode impl.
context_window_slim.json validated after session ID resolution.
**Verify:**
- Unit tests: reader parses valid/invalid/missing JSON
- Integration test: real Claude Code session has context_window_slim.json present
**Proceed when:** Reader reliably reads context state, presence validation catches missing hook.

### Gate 2: Signal + Directory + Instructions
**Scope:** R5, R6, R7, R9
**What:** New `/signal/self-compacted` endpoint. `private/PRIVATE.md` directory structure.
ContextForAgentProvider includes PRIVATE.md. Self-compaction instruction template.
**Verify:**
- Unit test: server routes self-compacted signal correctly
- Unit test: directory structure includes `private/`
- Unit test: instruction assembly includes PRIVATE.md when present, omits when absent
- Unit test: template renders correct content
**Proceed when:** Signal flows through server → deferred, instructions include PRIVATE.md,
template produces correct messages.

### Gate 3: `performCompaction()` — Done-Boundary Flow + Trigger Detection
**Scope:** R3, R4, R8
**What:** `performCompaction()` for `DONE_BOUNDARY`. Done-boundary trigger detection.
**Verify:**
- Unit test: `DONE_BOUNDARY` — done → low context → compaction instruction → SelfCompacted → session killed (lazy respawn)
- Unit test: `DONE_BOUNDARY` — done → healthy context → no compaction (normal flow)
- Unit test: session rotation → next use spawns new session with PRIVATE.md
- Unit test: `done` received during compaction → re-instruct → `SelfCompacted` → session killed
- Unit test: `done` received twice during compaction → `AgentCrashed`
**Proceed when:** Done-boundary compaction works in unit tests.

### Gate 4: Integration Validation
**Scope:** All requirements end-to-end
**What:** Integration test with real Claude Code agent confirming full flow.
**Verify:**
- context_window_slim.json is present and readable
- Self-compaction can be triggered at done boundary
- Session rotation produces a working new session with PRIVATE.md
**Proceed when:** Full flow works against real Claude Code.

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **PRIVATE.md quality varies** | Poor summarization → degraded agent performance in new session | Structured prompt template with specific sections. Iterate on template based on real-world results. |
| **Self-compaction itself fails** | Agent may not have enough context to summarize | 5-minute timeout. If fails → `AgentCrashed` → `FailedToExecutePlanUseCase`. |
| **External hook stops writing** | context_window_slim.json stale or missing mid-session | Validation after spawn catches missing files. Stale files → `remainingPercentage` returned as null → compaction is skipped with a logged warning → health monitoring's existing noActivityTimeout eventually catches a truly dead/silent agent. |
| **35% remaining triggers too early for some workloads** | Frequent unnecessary self-compactions | Threshold is a centralized constant, easily adjustable. Monitor in practice and tune. Each compaction adds ~2 minutes overhead. |
| **Context exhausted between done boundaries** | Agent degrades or crashes mid-task | Claude Code's native auto-compaction handles this. Health monitoring detects dead sessions. Granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) minimizes the gap between done boundaries. V2 adds harness-controlled emergency interrupt — see [`doc_v2/our-own-emergency-compression.md`](../../doc_v2/our-own-emergency-compression.md). |

---

## Open Questions

1. **Compaction count tracking:** Should `current_state.json` track how many times a sub-part
   self-compacted? Useful for observability and debugging. Low effort — add a
   `compactionCount: Int` field to the sub-part record.

2. **PRIVATE.md includes PUBLIC.md?** Should the self-compaction instruction tell the agent
   to incorporate PUBLIC.md content into PRIVATE.md? Or rely on PUBLIC.md being separately
   preserved and included in instructions? **Recommendation:** PRIVATE.md should be
   self-contained (include key PUBLIC.md content). The agent already has PUBLIC.md in
   context when summarizing, so it will naturally include relevant parts.

3. **Multiple consecutive compactions:** If an agent triggers self-compaction on every
   iteration (e.g., complex task that always fills context), the accumulated PRIVATE.md
   may grow large. Should there be a max compaction count per sub-part?
   **Recommendation:** Not in V1. Monitor and add if needed.
