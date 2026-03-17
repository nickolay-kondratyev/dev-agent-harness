# Context Window Self-Compaction — UseCase / ap.8nwz2AHf503xwq8fKuLcl.E

Detects context window exhaustion in TMUX-powered agents and performs controlled
self-compaction — killing the old session and spawning a fresh one with a `PRIVATE.md`
context summary. Replaces Claude Code's built-in auto-compaction (which is disabled)
with harness-controlled compaction at predictable boundaries.

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
automatically, but instructions and accumulated conversation can exceed limits. The agent
degrades silently — it may miss instructions, produce garbage, or loop. The existing health
monitor (ref.ap.6HIM68gd4kb8D2WmvQDUK.E) only checks "alive," not "functional."

**Solution:** Disable Claude Code's auto-compaction entirely. Monitor context window state
via an external hook that writes `context_window_slim.json`. At predictable thresholds,
the harness asks the agent to summarize its context into `PRIVATE.md`, kills the session,
and spawns a fresh one. The new session receives `PRIVATE.md` as part of its instructions —
a clean context window with compressed but complete prior knowledge.

---

## Vocabulary

| Term | Definition |
|------|------------|
| **Self-compaction** | Harness-controlled process: agent summarizes context → writes `PRIVATE.md` → signals `self-compacted` → harness kills session → spawns fresh session with `PRIVATE.md`. |
| **context_window_slim.json** | External hook artifact at `${HOME}/.vintrin_env/claude_code/session/<SessionID>/context_window_slim.json`. Written by a hook outside Shepherd after every conversation turn (when the agent stops thinking). Format: `{"file_updated_timestamp": "<ISO-8601 UTC>", "remaining_percentage": N}` where N is 0–100 (100 = fresh, 0 = exhausted). The `file_updated_timestamp` field is used for staleness detection — if the timestamp is older than `contextFileStaleTimeout`, the value is treated as stale (unknown). |
| **Soft threshold** | `remaining_percentage ≤ SELF_COMPACTION_SOFT_THRESHOLD` (default: 35). Triggers when the agent has **used 65%** of its context (35% remaining). Checked at `done` boundaries — proactive compaction while the agent still has room to produce a quality summary. |
| **Hard threshold** | `remaining_percentage ≤ SELF_COMPACTION_HARD_THRESHOLD` (default: 20). Triggers when the agent has **used 80%** of its context (20% remaining). Checked continuously (1-second poll). Triggers emergency mid-task interrupt + forced self-compaction. |
| **Session rotation** | Kill old TMUX session → spawn new one for the same sub-part. New HandshakeGuid, new session record in `sessionIds` array. |
| **PRIVATE.md** | Agent's self-compaction summary. Written to `${sub_part}/private/PRIVATE.md` in `.ai_out/`. Contains compressed but context-rich summary of the agent's work, decisions, and challenges. |

---

## Two Thresholds, One Flow

Both thresholds trigger the **same** core compaction flow — `performCompaction()`. The only
differences are the **entry conditions** (how the compaction is triggered) and the
**post-compaction behavior** (lazy vs immediate respawn). Unifying these into a single flow
eliminates the risk of the two paths diverging over time and makes compaction testable as
one well-exercised code path.

### CompactionTrigger

```kotlin
enum class CompactionTrigger {
    /** Agent at done boundary with ≤35% context remaining. No interrupt needed. */
    DONE_BOUNDARY,
    /** Agent mid-task with ≤20% context remaining. Requires Ctrl+C interrupt first. */
    EMERGENCY_INTERRUPT,
}
```

### Trigger Detection

| Trigger | Where detected | Condition | Q&A gate |
|---------|---------------|-----------|----------|
| `DONE_BOUNDARY` | After `AgentSignal.Done` + PUBLIC.md validation (ref.ap.THDW9SHzs1x2JN9YP9OYU.E) | `remaining_percentage ≤ SELF_COMPACTION_SOFT_THRESHOLD` (default: 35) | N/A — done boundary implies no Q&A pending |
| `EMERGENCY_INTERRUPT` | Health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E), every ~1 second | `remaining_percentage ≤ SELF_COMPACTION_HARD_THRESHOLD` (default: 20) | **Skipped** when `SessionEntry.isQAPending` is true (ref.ap.NE4puAzULta4xlOLh5kfD.E) |

**Q&A suppression:** When Q&A is pending (`isQAPending == true`), the agent is idle awaiting
a TMUX answer — its context window is not growing. Emergency compaction is unnecessary and
would interfere with the Q&A coordinator's answer delivery. The health-aware await loop skips
the entire compaction + health check block while `isQAPending` is true.

### Unified Compaction Flow — `performCompaction(handle, trigger)`

```
performCompaction(handle, trigger: CompactionTrigger):
    │
    ├─ 1. Pre-compaction (trigger-dependent):
    │   ├─ DONE_BOUNDARY: no-op (agent already idle at done boundary)
    │   └─ EMERGENCY_INTERRUPT:
    │       ├─ Race condition guard: if signalDeferred.isCompleted → return signal (skip compaction)
    │       ├─ Send Ctrl+C via AgentFacade (interrupt agent mid-task)
    │       └─ Brief pause (1-2 seconds) for interrupt to take effect
    │
    ├─ 2. Core compaction (shared — identical for both triggers):
    │   ├─ Send self-compaction instruction via AgentFacade.sendPayload(config, handle, payload)
    │   │   (creates fresh signal deferred implicitly — ref.ap.9h0KS4EOK5yumssRCJdbq.E)
    │   │   (EMERGENCY_INTERRUPT: prepend interrupt acknowledgment prefix — see Instruction Message)
    │   │   → update handle with returned value
    │   ├─ Await AgentSignal.SelfCompacted on handle.signal (with timeout: SELF_COMPACTION_TIMEOUT)
    │   ├─ Validate PRIVATE.md exists and is non-empty
    │   ├─ Git commit — captures PRIVATE.md + any changes before compaction
    │   └─ Kill session via AgentFacade.killSession(handle) → set handle = null
    │
    └─ 3. Post-compaction (trigger-dependent):
        ├─ DONE_BOUNDARY: handle is now null. Continue normal flow (next sub-part, iteration
        │   restart, etc.). The next sendPayload call will transparently spawn a fresh session.
        └─ EMERGENCY_INTERRUPT: immediate respawn via sendPayload:
            ├─ handle = agentFacade.sendPayload(config, null, instructions)
            │   (null handle → AgentFacade spawns fresh session — ref.ap.hZdTRho3gQwgIXxoUtTqy.E)
            │   (new session receives instructions including PRIVATE.md via
            │   ContextForAgentProvider — ref.ap.9HksYVzl1KkR9E1L2x8Tx.E)
            └─ Enter new health-aware await loop on handle.signal
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

### Entry Point: Health-Aware Await Loop (Hard Threshold)

```
Health-aware await loop (polling every 1 second)
    │
    ├─ Check: signal arrived? → return signal (normal path)
    │
    ├─ Read context_window_slim.json
    │   ├─ remaining_percentage > SELF_COMPACTION_HARD_THRESHOLD → continue await loop
    │   └─ remaining_percentage ≤ SELF_COMPACTION_HARD_THRESHOLD:
    │       └─ performCompaction(sessionEntry, EMERGENCY_INTERRUPT)
    │
    └─ Regular health checks (at normal intervals — ref.ap.6HIM68gd4kb8D2WmvQDUK.E)
```

**Race condition: `done` arrives between detection and interrupt.** The `performCompaction()`
pre-compaction step for `EMERGENCY_INTERRUPT` checks if the deferred was completed before
sending Ctrl+C. If `Done` arrived, it returns the signal — the executor then falls through
to the done-boundary path which may trigger `performCompaction(DONE_BOUNDARY)` if the soft
threshold is also crossed. No interrupt sent.

---

## ContextWindowStateReader / ap.ufavF1Ztk6vm74dLAgANY.E

Agent-type-specific interface for reading context window state. Follows the same OCP
pattern as `AgentStarter` (ref.ap.RK7bWx3vN8qLfYtJ5dZmQ.E) and `AgentSessionIdResolver`
(ref.ap.D3ICqiFdFFgbFIPLMTYdoyss.E) — one implementation per agent type.

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

- `validatePresence()` called after `AgentSessionIdResolver` resolves the session ID
  (step 6a in spawn flow — ref.ap.hZdTRho3gQwgIXxoUtTqy.E). Confirms hook is active
  before any work begins.

### Caller Behavior on Stale State

Both compaction trigger sites (done-boundary check and emergency-interrupt poll) must handle
`remainingPercentage == null`:

```
contextState = agentFacade.readContextWindowState(handle)
if (contextState.remainingPercentage == null) {
    // Log warning: "context_window_slim.json is stale — skipping compaction check"
    // Do NOT trigger compaction. Continue normal flow.
    continue
}
if (contextState.remainingPercentage <= threshold) {
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

### Fallback: Agent Signals `done` Instead of `self-compacted`

If the agent misunderstands the compaction instruction and signals `done` instead of
`self-compacted`, the executor receives `Done`. It should:
1. Check if `PRIVATE.md` was written (the agent may have done the right thing, wrong signal)
2. If `PRIVATE.md` exists → proceed with session rotation (treat as successful compaction)
3. If `PRIVATE.md` missing → re-instruction (one retry, same pattern as PUBLIC.md retry —
   ref.ap.THDW9SHzs1x2JN9YP9OYU.E)

---

## Self-Compaction Instruction Message / ap.kY4yu9B3HGvN66RoDi0Fb.E

Sent to the agent via `AckedPayloadSender` (ref.ap.tbtBcVN2iCl1xfHJthllP.E) when
self-compaction is triggered (either threshold).

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

**For `EMERGENCY_INTERRUPT` trigger**, the message is prepended with:

```markdown
You were interrupted because your context window is critically low.
Do NOT continue your previous task. Focus only on writing the summary below.
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

## Auto-Compaction Disabled — Prerequisite

### Why Disable Auto-Compaction

Claude Code has built-in auto-compaction that triggers when the context window fills up.
This compaction:
- Happens at unpredictable times
- Loses quality (summarization is automatic, not guided)
- Interferes with our controlled self-compaction

By disabling auto-compaction, the harness becomes the **sole controller** of context
management. Self-compaction happens at predictable thresholds with guided summarization.

### How Auto-Compaction Is Disabled / ap.7bD0uLeoQQSFS16TQeCRF.E

Two mechanisms are required (belt and suspenders):

**1. Config file: `~/.claude.json`** (the **app-state** file) must contain
`"autoCompactEnabled": false`.

Written **once at harness startup** by `EnvironmentValidator` using Kotlin/Jackson JSON
handling (no jq dependency). The logic:
- If `~/.claude.json` does not exist → create it with `{"autoCompactEnabled":false}`
- If it exists → parse as JSON (Jackson `ObjectMapper`), set `autoCompactEnabled = false`,
  write back atomically (write to temp file, rename)

<!-- WHY-NOT(2026-03-14): Do NOT use ~/.claude/settings.json for autoCompactEnabled —
     it silently ignores this key. Only ~/.claude.json (the app-state file) works.
     Ref: https://github.com/anthropics/claude-code/issues/6689 -->

> **`~/.claude/settings.json` silently ignores `autoCompactEnabled`.** Only `~/.claude.json`
> works. This is a known Claude Code behavior
> (ref: https://github.com/anthropics/claude-code/issues/6689).

<!-- WHY-NOT(2026-03-17): Do NOT use jq for ~/.claude.json manipulation — the harness is
     a Kotlin/JVM application and already has Jackson on the classpath. Using jq would add
     an external binary dependency to the spawn path, introduce file I/O failure modes on
     every spawn, and create race conditions when concurrent agents spawn simultaneously.
     Jackson handles JSON natively, runs in-process, and the write happens once at startup. -->

**2. Env var: `DISABLE_AUTO_COMPACT=true`** must be exported in the TMUX session
environment.

```bash
export DISABLE_AUTO_COMPACT=true
```

Both are required. The config file persists the setting; the env var is the runtime
enforcement per session.

### Harness Responsibilities — Startup + Per-Spawn

| When | What | Where |
|------|------|-------|
| **Harness startup** | `EnvironmentValidator` (ref.ap.A8WqG9oplNTpsW7YqoIyX.E) reads `~/.claude.json`, ensures `autoCompactEnabled == false` is set. If the key is missing or `true`, writes the correct value using Jackson (read → merge → atomic write). Hard fail if the file is unparseable. This is the **only place** that writes `~/.claude.json`. | `EnvironmentValidator.validate()` |
| **Every agent spawn** | `ClaudeCodeAgentStarter` (ref.ap.RK7bWx3vN8qLfYtJ5dZmQ.E) exports `DISABLE_AUTO_COMPACT=true` in the TMUX session env (alongside `TICKET_SHEPHERD_HANDSHAKE_GUID` and `TICKET_SHEPHERD_SERVER_PORT`). No config file write — that was done once at startup. | `ClaudeCodeAgentStarter.buildStartCommand()` |
| **Every session rotation** | Same as "Every agent spawn" — env var export is inherent in the spawn command. | Implicit — covered by spawn flow |

<!-- WHY(2026-03-17): Config file write moved from per-spawn to startup-only.
     Rationale:
     - The harness is the sole manager of Claude Code sessions; no external process
       re-enables auto-compaction mid-run.
     - The env var (DISABLE_AUTO_COMPACT=true) is the per-session runtime guarantee.
     - Writing once at startup eliminates: jq dependency, per-spawn file I/O failure modes,
       race conditions on concurrent spawns.
     - If somehow the file is modified externally, the env var still prevents auto-compaction. -->

---

## Health-Aware Await Loop Change / ref.ap.QCjutDexa2UBDaKB3jTcF.E

### Current Loop Structure (Before This Change)

```
while (true) {
    signal = awaitSignalWithTimeout(healthCheckInterval)  // 5 min
    if (signal != null) return signal
    // health checks at 5-minute intervals...
}
```

### Updated Loop Structure

The loop now polls every **1 second** for context window state while maintaining the
existing health check intervals:

```
// Phase B (updated): Signal-Await with Context Window Monitoring + Health Checks
lastHealthCheck = now()
while (true) {
    signal = awaitSignalWithTimeout(1.second)

    if (signal != null) {
        return signal  // Done, FailWorkflow, SelfCompacted
    }

    // --- Q&A pending gate: skip compaction + health checks while Q&A is active ---
    // Agent is known-idle awaiting TMUX answer — context not growing, pings waste context.
    // See UserQuestionHandler (ref.ap.NE4puAzULta4xlOLh5kfD.E).
    if (sessionEntry.isQAPending) {
        continue
    }

    // --- Context window check for compaction (every iteration = every ~1 second) ---
    contextState = agentFacade.readContextWindowState(handle)
    if (contextState.remainingPercentage <= HARD_THRESHOLD) {
        // performCompaction handles race condition guard (isCompleted check),
        // Ctrl+C interrupt, and immediate respawn internally.
        return performCompaction(sessionEntry, CompactionTrigger.EMERGENCY_INTERRUPT)
    }

    // --- Health checks: lastActivityTimestamp only (at normal intervals) ---
    // Liveness is determined solely by HTTP callbacks — see HealthMonitoring.md
    // (ref.ap.dnc1m7qKXVw2zJP8yFRE.E). context_window_slim.json is used for
    // compaction decisions (above) only, NOT for liveness.
    elapsed = now() - lastHealthCheck
    if (elapsed >= healthCheckInterval) {
        lastHealthCheck = now()
        // health check logic: lastActivityTimestamp staleness, ping, crash detection
        ...
    }
}
```

**Key:** The 1-second poll interval applies to `awaitSignalWithTimeout`, NOT to health
checks. Health checks still fire at their normal intervals (5 min default). Context window
state is checked every iteration (~1 second) for compaction purposes. File I/O overhead
is negligible — the file is a single-line JSON on local filesystem.

---

## Hard Constraint Modification / ref.ap.NAVMACFCbnE7L6Geutwyk.E (high-level.md)

### Before

> **One TMUX session per sub-part.** A sub-part gets exactly one TMUX session, spawned on
> first run and kept alive across iteration loops. The session is killed only when the
> **part** completes.

### After

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
check in the executor. This also covers idle session death: if `send-keys` fails, the
facade respawns transparently — no `if/else` branching needed.

All agent operations go through `AgentFacade` (ref.ap.9h0KS4EOK5yumssRCJdbq.E). The
executor is agnostic to session lifecycle — it always calls one method regardless of
session state.

**DRY between planning and execution:** The `PartExecutor` is already shared between
planning (PLANNER↔PLAN_REVIEWER) and execution parts. The self-compaction logic lives in
the executor, so it applies to both without duplication.

---

## context_window_slim.json Validation After Session ID Resolution

After `AgentSessionIdResolver` resolves the session ID (step 6a in spawn flow —
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

## Session Rotation Detail

The **core compaction steps** (shared by both triggers) execute via `AgentFacade`
(ref.ap.9h0KS4EOK5yumssRCJdbq.E):

1. **Validate PRIVATE.md:** Check `${sub_part}/private/PRIVATE.md` exists and is non-empty.
   If missing after one retry → `PartResult.AgentCrashed`.
2. **Git commit:** `GitCommitStrategy.onSubPartDone` — captures PRIVATE.md + any other
   changes the agent made before compaction.
3. **Kill session:** `agentFacade.killSession(handle)` — internally kills TMUX session
   and cleans up `SessionsState`.

**Post-compaction — trigger-dependent:**

**`DONE_BOUNDARY`:** `handle = null`. No immediate respawn. The next time the executor
needs this sub-part, it calls `agentFacade.sendPayload(config, null, instructions)` —
AgentFacade spawns a fresh session transparently (standard spawn flow —
ref.ap.hZdTRho3gQwgIXxoUtTqy.E).

**`EMERGENCY_INTERRUPT`:** Immediate respawn via unified call:
4. **Spawn + send instructions:** `handle = agentFacade.sendPayload(config, null, instructions)`
   — null handle triggers AgentFacade to spawn a fresh session (new `HandshakeGuid`, new TMUX
   session, bootstrap handshake, session ID resolution + context_window_slim.json validation,
   new entry in `sessionIds` array). `ContextForAgentProvider` assembles instructions including
   `PRIVATE.md`. Returns a fresh `SpawnedAgentHandle` with a fresh signal deferred.
5. **Enter health-aware await loop:** Normal monitoring resumes on the new `handle.signal`.

---

## Interaction with Other Use Cases

### Granular Feedback Loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E)

The inner feedback loop creates **frequent done boundaries** — one after each feedback item,
and additional boundaries during per-item rejection negotiation. Each done boundary is a
natural soft-compaction checkpoint. This significantly reduces the need for emergency
compaction (hard threshold): the doer processes one item, hits a done boundary, compacts if
needed, then starts fresh on the next item. Without the inner loop, the doer processes ALL
feedback in one go, making mid-task context exhaustion more likely.

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
| `SELF_COMPACTION_HARD_THRESHOLD` | `contextWindowHardThresholdPct` | 20 | Emergency interrupt when `remaining_percentage ≤ 20` (i.e., 80% of context used, 20% remaining) |
| `SELF_COMPACTION_TIMEOUT` | `selfCompactionTimeout` | 5 min | Max time for agent to write PRIVATE.md and signal |
| `contextFileStaleTimeout` | `HarnessTimeoutConfig` field | 5 min | Maximum age of `file_updated_timestamp` in `context_window_slim.json` before the value is considered stale. When stale: `remainingPercentage` is treated as null (unknown) — no compaction is triggered, a warning is logged. This guards against the hook silently stopping mid-session and the harness acting on a frozen `remaining_percentage` that looks healthy but is minutes old. Uses `file_updated_timestamp` from the JSON body (not OS file mtime). |

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
| `doc/core/PartExecutor.md` | Health-aware await loop updated structure, session existence check in re-instruction path, self-compaction step after done |
| `doc/use-case/HealthMonitoring.md` | New use case reference, 1-second poll integration |
| `doc/core/ContextForAgentProvider.md` | PRIVATE.md in instruction concatenation (position 1b) |
| `doc/schema/ai-out-directory.md` | New `private/PRIVATE.md` in directory tree, supersede "No PRIVATE.md" section |
| `doc/core/agent-to-server-communication-protocol.md` | New `/signal/self-compacted` endpoint |
| `doc/use-case/SpawnTmuxAgentSessionUseCase.md` | context_window_slim.json validation after session ID resolution |
| `doc/core/SessionsState.md` | Clarification: multiple session records per sub-part after rotation |
| `doc/high-level.md` (Key Technology Decisions) | Auto-compaction disabled entry, context window monitoring entry |

---

## Requirements

### R1: ContextWindowStateReader Interface + ClaudeCode Implementation
- Interface with `read(agentSessionId)` and `validatePresence(agentSessionId)` methods
- `ClaudeCodeContextWindowStateReader` reads from `${HOME}/.vintrin_env/claude_code/session/<agentSessionId>/context_window_slim.json`
- File missing → `ContextWindowStateUnavailableException` (hard stop)
- File malformed (missing required fields or unparseable JSON) → same exception with parse error details
- File present, `file_updated_timestamp` older than `contextFileStaleTimeout` → `ContextWindowState(remainingPercentage = null)` + log warning (not an exception)
- `ContextWindowState.remainingPercentage` is nullable (`Int?`); null = stale/unknown
- Callers (done-boundary check, emergency-interrupt poll): skip compaction trigger when `remainingPercentage == null`, log warning instead
- Verifiable: unit test with fake file (fresh timestamp → value returned); unit test (stale timestamp → null returned + warning logged); unit test (file missing → exception); integration test confirming file presence after real Claude Code session

### R2: Auto-Compaction Disabled — Startup Write + Per-Spawn Env Var
- Full mechanism spec: ref.ap.7bD0uLeoQQSFS16TQeCRF.E
- `EnvironmentValidator` at harness startup: reads `~/.claude.json`, ensures `autoCompactEnabled == false` using Jackson (Kotlin/JVM JSON — no jq dependency). If missing or wrong, writes the correct value (read → merge → atomic write). Hard fail if unparseable.
- `ClaudeCodeAgentStarter` on **every spawn** (including session rotations): exports `DISABLE_AUTO_COMPACT=true` in TMUX session env. No config file write — that was done once at startup.
- NOT `~/.claude/settings.json` — that file silently ignores `autoCompactEnabled` (ref: github.com/anthropics/claude-code/issues/6689)
- Verifiable: startup validation test (write + verify); unit test: starter command includes `DISABLE_AUTO_COMPACT=true`; integration test confirming Claude Code does not auto-compact

### R3: context_window_slim.json Validation After Session ID Resolution
- After `AgentSessionIdResolver` resolves session ID, call `contextWindowStateReader.validatePresence()`
- Missing file → `PartResult.AgentCrashed` with clear error about hook misconfiguration
- Verifiable: unit test — mock resolver returns ID, mock reader throws → executor returns AgentCrashed

### R4: Health-Aware Await Loop Polls Context State Every 1 Second
- `awaitSignalWithTimeout(1.second)` replaces `awaitSignalWithTimeout(healthCheckInterval)`
- Context window state read on every iteration
- Hard threshold check: `remaining_percentage ≤ 20` → emergency compaction
- Regular health checks still fire at normal intervals (tracked by elapsed time)
- Verifiable: unit test — mock reader returning decreasing values → executor triggers emergency compaction at threshold

### R5: `performCompaction()` — Unified Compaction Flow

Single method handling both triggers via `CompactionTrigger` enum:
- `DONE_BOUNDARY`: no pre-compaction interrupt; post-compaction = lazy respawn
- `EMERGENCY_INTERRUPT`: pre-compaction = race guard + Ctrl+C + pause; post-compaction = immediate respawn + send instructions + resume monitoring
- Core steps shared: reset deferred → send instruction → await `SelfCompacted` → validate PRIVATE.md → git commit → kill session
- Instruction message uses interrupt-acknowledgment prefix for `EMERGENCY_INTERRUPT`
- Verifiable: unit test — both trigger variants exercise the same core steps; only pre/post differ

### R6: Trigger Detection
- **Done boundary** (`DONE_BOUNDARY`): after `AgentSignal.Done` + PUBLIC.md validation, read context window state; if `remaining_percentage ≤ SELF_COMPACTION_SOFT_THRESHOLD` (default 35) → call `performCompaction(DONE_BOUNDARY)`
- **Emergency interrupt** (`EMERGENCY_INTERRUPT`): health-aware await loop detects `remaining_percentage ≤ SELF_COMPACTION_HARD_THRESHOLD` (default 20) → call `performCompaction(EMERGENCY_INTERRUPT)`
- Verifiable: unit test — done signal → low context → `DONE_BOUNDARY` compaction → session killed; unit test — polling detects hard threshold → `EMERGENCY_INTERRUPT` compaction

### R7: Self-Compacted Signal Endpoint
- New endpoint `/callback-shepherd/signal/self-compacted`
- New `AgentSignal.SelfCompacted` variant
- Server completes `signalDeferred` with `SelfCompacted`
- Updates `lastActivityTimestamp`
- Callback script: `callback_shepherd.signal.sh self-compacted`
- Verifiable: unit test — server receives self-compacted → deferred completed correctly

### R8: PRIVATE.md in .ai_out/ Directory Structure
- New path: `${sub_part}/private/PRIVATE.md`
- Created by agent during self-compaction
- Overwritten on subsequent self-compactions (git preserves history)
- `AiOutputStructure` creates `private/` directory alongside `comm/`
- Verifiable: directory creation test

### R9: ContextForAgentProvider Includes PRIVATE.md
- Check for `private/PRIVATE.md` in sub-part directory
- If exists and non-empty → include in instruction assembly at position 1b (after role def, before part context)
- If not exists → skip silently (no error)
- Applies to all instruction assembly methods (execution, planner, plan-reviewer)
- Verifiable: unit test — instructions with/without PRIVATE.md present

### R10: Session Rotation
- After self-compaction: `agentFacade.killSession()` (internally kills TMUX, cleans
  SessionsState), set `handle = null`
- Next `agentFacade.sendPayload(config, null, instructions)` transparently spawns fresh session
- New HandshakeGuid, new session record in `sessionIds` array
- PRIVATE.md picked up by ContextForAgentProvider in new instructions
- Works for both planning and execution parts (DRY via shared PartExecutor)
- Verifiable: unit test via `FakeAgentFacade` — full rotation sequence;
  integration test — real session rotation

### R11: Self-Compaction Instruction Message
- Template includes: PRIVATE.md path, summarization guidelines, signal instruction
- Emergency variant includes interrupt acknowledgment prefix
- Verifiable: template renders correct paths and signal commands

---

## Implementation Gates

### Gate 1: Foundation — Reader + Validation + Auto-Compaction Off
**Scope:** R1, R2, R3
**What:** ContextWindowStateReader interface + ClaudeCode impl. EnvironmentValidator
writes `~/.claude.json` with `autoCompactEnabled: false` at startup (Jackson, no jq).
ClaudeCodeAgentStarter exports `DISABLE_AUTO_COMPACT=true` env var per spawn.
context_window_slim.json validated after session ID resolution.
**Verify:**
- Unit tests: reader parses valid/invalid/missing JSON
- Unit test: startup validation rejects missing/wrong `~/.claude.json`
- Unit test: starter command includes `DISABLE_AUTO_COMPACT=true`
- Integration test: real Claude Code session has context_window_slim.json present
**Proceed when:** Reader reliably reads context state, auto-compaction is confirmed disabled,
presence validation catches missing hook.

### Gate 2: Signal + Directory + Instructions
**Scope:** R7, R8, R9, R11
**What:** New `/signal/self-compacted` endpoint. `private/PRIVATE.md` directory structure.
ContextForAgentProvider includes PRIVATE.md. Self-compaction instruction template.
**Verify:**
- Unit test: server routes self-compacted signal correctly
- Unit test: directory structure includes `private/`
- Unit test: instruction assembly includes PRIVATE.md when present, omits when absent
- Unit test: template renders correct content
**Proceed when:** Signal flows through server → deferred, instructions include PRIVATE.md,
template produces correct messages.

### Gate 3: `performCompaction()` — Unified Compaction Flow + Trigger Detection
**Scope:** R4, R5, R6, R10
**What:** `CompactionTrigger` enum, `performCompaction()` with shared core + trigger-specific
pre/post steps. Done-boundary trigger detection. 1-second polling loop with emergency trigger detection.
**Verify:**
- Unit test: `DONE_BOUNDARY` — done → low context → compaction instruction → SelfCompacted → session killed (lazy respawn)
- Unit test: `DONE_BOUNDARY` — done → healthy context → no compaction (normal flow)
- Unit test: `EMERGENCY_INTERRUPT` — polling detects hard threshold → Ctrl+C → compaction → rotate → respawn → resume
- Unit test: `EMERGENCY_INTERRUPT` race condition guard — done arrives before interrupt → no Ctrl+C sent
- Unit test: session rotation → next use spawns new session with PRIVATE.md
- Unit test: fallback when agent signals `done` instead of `self-compacted`
- Unit test: health checks still fire at normal intervals despite 1-second loop
**Proceed when:** Both trigger variants of `performCompaction()` work in unit tests.

### Gate 4: Integration Validation
**Scope:** All requirements end-to-end
**What:** Integration test with real Claude Code agent confirming full flow.
**Verify:**
- Session spawns with auto-compaction disabled
- context_window_slim.json is present and readable
- Self-compaction can be triggered (may require synthetic context filling)
- Session rotation produces a working new session with PRIVATE.md
**Proceed when:** Full flow works against real Claude Code.

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Ctrl+C timing corrupts files** | Agent mid-tool-use when interrupted; files may be partially written | Git commit before compaction captures last-known-good state. Agent's first task in new session can detect and fix corruption via `git status`. |
| **PRIVATE.md quality varies** | Poor summarization → degraded agent performance in new session | Structured prompt template with specific sections. Iterate on template based on real-world results. |
| **1-second polling overhead** | Continuous file reads during entire agent work period | File is ~30 bytes JSON on local filesystem. Measured overhead: negligible (< 0.1ms per read). |
| **Self-compaction itself fails** | Agent at 20% remaining may not have enough context to summarize | 5-minute timeout. If fails → `AgentCrashed` → `FailedToExecutePlanUseCase`. Consider shorter PRIVATE.md template for emergency compaction. |
| **External hook stops writing** | context_window_slim.json stale or missing mid-session | Validation after spawn catches missing files. Stale files (file_updated_timestamp > contextFileStaleTimeout) → `remainingPercentage` returned as null → compaction is skipped with a logged warning → health monitoring's existing noActivityTimeout eventually catches a truly dead/silent agent. |
| **35% remaining triggers too early for some workloads** | Frequent unnecessary self-compactions | Threshold is a centralized constant, easily adjustable. Monitor in practice and tune. Each compaction adds ~2 minutes overhead. |

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
