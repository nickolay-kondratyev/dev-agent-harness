# V2: Our Own Emergency Compression (Harness-Controlled Emergency Interrupt)

## Status: DEFERRED — Not in V1

V1 relies on **Claude Code's native auto-compaction** for emergency context exhaustion between
done boundaries. This document preserves the V2 design for **harness-controlled emergency
interrupt compaction** — the more robust but significantly more complex approach.

See V1 simplification rationale: the granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E)
creates frequent done boundaries, making emergency interrupt between boundaries rare. When it
does happen, Claude Code's built-in auto-compaction handles it adequately for V1.

---

## What This Adds Over V1

V1 has only the **soft threshold at done boundaries** (35% remaining). If an agent exhausts
its context between done boundaries, Claude Code's native auto-compaction kicks in.

V2 adds a **hard threshold** (20% remaining) with continuous 1-second polling and
harness-controlled emergency interrupt (Ctrl+C) mid-task.

---

## Design (Preserved from Original Spec)

### Hard Threshold — `SELF_COMPACTION_HARD_THRESHOLD`

| Parameter | Value |
|-----------|-------|
| `SELF_COMPACTION_HARD_THRESHOLD` | 20 (remaining percentage) |
| `contextWindowHardThresholdPct` | `HarnessTimeoutConfig` field |
| Detection | Health-aware await loop, every ~1 second |
| Trigger | `remaining_percentage ≤ 20` |
| Q&A gate | Skipped when `SessionEntry.isQAPending` is true |

### CompactionTrigger Enum Extension

```kotlin
enum class CompactionTrigger {
    /** Agent at done boundary with ≤35% context remaining. No interrupt needed. */
    DONE_BOUNDARY,
    /** Agent mid-task with ≤20% context remaining. Requires Ctrl+C interrupt first. */
    EMERGENCY_INTERRUPT,
}
```

### Emergency Interrupt Flow

```
performCompaction(handle, EMERGENCY_INTERRUPT):
    │
    ├─ 1. Pre-compaction:
    │   ├─ Race condition guard: if signalDeferred.isCompleted → return signal (skip compaction)
    │   ├─ Send Ctrl+C via AgentFacade.sendRawKeys() (interrupt agent mid-task)
    │   └─ Brief pause (1-2 seconds) for interrupt to take effect
    │
    ├─ 2. Core compaction (shared with DONE_BOUNDARY):
    │   ├─ Send self-compaction instruction via AgentFacade
    │   │   (prepend interrupt acknowledgment prefix for EMERGENCY_INTERRUPT)
    │   ├─ Await AgentSignal.SelfCompacted (with timeout: SELF_COMPACTION_TIMEOUT)
    │   ├─ Validate PRIVATE.md exists and is non-empty
    │   ├─ Git commit — captures PRIVATE.md + any changes before compaction
    │   └─ Kill session via AgentFacade.killSession(handle)
    │
    └─ 3. Post-compaction:
        └─ EMERGENCY_INTERRUPT: immediate respawn
            ├─ Spawn fresh session (new HandshakeGuid)
            ├─ Instructions include PRIVATE.md via ContextForAgentProvider
            └─ Enter new health-aware await loop
```

### Interrupt Acknowledgment Prefix

For `EMERGENCY_INTERRUPT`, the compaction instruction is prepended with:

```markdown
You were interrupted because your context window is critically low.
Do NOT continue your previous task. Focus only on writing the summary below.
```

### Race Condition Guard

The `performCompaction()` pre-compaction step for `EMERGENCY_INTERRUPT` checks if the
deferred was completed before sending Ctrl+C. If `Done` arrived between detection and
interrupt, it returns the signal — the executor then falls through to the done-boundary
path which may trigger `performCompaction(DONE_BOUNDARY)` if the soft threshold is also
crossed. No interrupt sent.

### Ctrl+C Delivery

Uses `TmuxCommunicator.sendRawKeys()` which sends without `-l` (literal flag), allowing
control sequences. `sendKeys()` (with `-l`) is for content delivery.

---

## Why This Is Complex

The emergency interrupt path is the most complex code path in the system:

1. **Race condition guard** — done signal vs Ctrl+C timing
2. **Interrupt acknowledgment prefix** in compaction instructions
3. **Ctrl+C via TMUX `sendRawKeys()`** — agent may be mid-tool-use
4. **File corruption risk** from interrupted writes
5. **Git commit before compaction** to capture last-known-good state
6. **Immediate respawn** with PRIVATE.md (unlike done-boundary lazy respawn)

---

## Why V1 Can Skip This

1. **Granular feedback loop** (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) creates frequent done
   boundaries — one after each feedback item. This makes context exhaustion between done
   boundaries rare for typical tasks.
2. **Claude Code's native auto-compaction** handles the rare case where context IS exhausted
   between done boundaries. While less controlled than harness-managed compaction, it is
   adequate for V1.
3. **Health monitoring catches dead sessions** — if context is exhausted and the agent session
   dies, the harness detects via health monitoring and returns `AgentCrashed` (existing mechanism).
4. **35% soft threshold is generous** — 70K tokens remaining on a 200K window.
5. **V1 has serial execution** — only one agent active, context usage is predictable.

---

## Auto-Compaction Configuration (V1 Simplification)

In V1, Claude Code's auto-compaction is **NOT disabled**. The harness does NOT export
`DISABLE_AUTO_COMPACT=true`.

Claude Code handles its own emergency compaction natively. The harness only performs
controlled self-compaction at done boundaries (soft threshold).

### V2 Change: Disable Auto-Compaction

When implementing this V2 feature, auto-compaction must be disabled to give the harness
sole control. Use **only `DISABLE_AUTO_COMPACT=true`** — exported per TMUX session by
`ClaudeCodeAdapter`.

- **No `.claude.json` config required** — env var is set per-session, not per-machine.
  Single source of truth: if compaction isn't disabled, check the env var.
- No host-level setup dependency — harness is self-contained.

<!-- WHY-NOT .claude.json: file-system dependency outside repo/harness control; per-machine
     not per-session; `.claude/settings.json` silently ignores `autoCompactEnabled` making
     it easy to misconfigure. Env var is explicit, per-session, and logged. -->

---

## Risks (V2-Specific)

| Risk | Impact | Mitigation |
|------|--------|------------|
| Ctrl+C timing corrupts files | Agent mid-tool-use when interrupted; files partially written | Git commit before compaction captures last-known-good state |
| Self-compaction at 20% may not have enough context | Agent fails to summarize | 5-minute timeout → `AgentCrashed` → `FailedToExecutePlanUseCase` |
| Race condition between done and Ctrl+C | Spurious interrupt after agent already done | Pre-compaction guard checks `signalDeferred.isCompleted` |

---

## Health-Aware Await Loop Change (V2)

The loop polls every **1 second** (instead of only at health check intervals) for
context window state:

```
// V2 addition: Emergency compaction check in health-aware await loop
contextState = contextWindowStateReader.read(agentSessionId)
if (contextState.remainingPercentage <= HARD_THRESHOLD) {
    return performCompaction(sessionEntry, CompactionTrigger.EMERGENCY_INTERRUPT)
}
```

This check is inserted before the health check in the 1-second tick loop.

---

## Spec Files That Will Need Updates (When Implementing V2)

- `doc/use-case/ContextWindowSelfCompactionUseCase.md` — restore `EMERGENCY_INTERRUPT` flow
- `doc/core/PartExecutor.md` — restore hard threshold check in await loop pseudocode
- `doc/high-level.md` — restore hard threshold in Context Window section
- `doc/core/agent-to-server-communication-protocol.md` — note `sendRawKeys()` for Ctrl+C
- `doc/use-case/HealthMonitoring.md` — restore emergency compaction reference
