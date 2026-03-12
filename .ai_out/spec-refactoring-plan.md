# Spec Refactoring Plan / ap.Zzij5wzu3d3ZNhxhvRBbi.E

> **Goal**: Make the `doc/` specs more analyzable by eliminating cross-document duplication,
> extracting implementation-level detail, and making `high-level.md` a true summary document.
>
> **Principle**: Every concept has ONE authoritative location. All other mentions use `ref.ap.XXX`.
> If you need the full detail, follow the AP. If you just need context, the summary sentence
> next to the ref is enough.

---

## Current State — Line Counts

| Doc | Lines | Verdict |
|-----|------:|---------|
| `doc/high-level.md` | 360 | **Too long** — doing triple duty as summary + spec + overflow |
| `doc/core/agent-to-server-communication-protocol.md` | 335 | **Too long** — packs 4 concerns |
| `doc/schema/plan-and-current-state.md` | 335 | Borderline — JSON examples inflate it but are useful |
| `doc/core/PartExecutor.md` | 238 | **Pseudocode problem** — flows read like implementation |
| `doc/core/ContextForAgentProvider.md` | 235 | **Data classes inflate** — Kotlin code that's impl not spec |
| `doc/core/git.md` | 148 | Acceptable |
| `doc/core/SessionsState.md` | 126 | **Impl rationale** — concurrency section is code-level |
| `doc/schema/ai-out-directory.md` | 99 | ✓ Good |
| `doc/use-case/SpawnTmuxAgentSessionUseCase.md` | 96 | ✓ Good |
| `doc/use-case/SetupPlanUseCase/DetailedPlanningUseCase.md` | 79 | ✓ Good |
| `doc/core/TicketShepherd.md` | 78 | ✓ Good |
| `doc/use-case/SetupPlanUseCase/__this.md` | 42 | ✓ Good |
| `doc/core/TicketShepherdCreator.md` | 40 | ✓ Good |
| `doc/use-case/SetupPlanUseCase/StraightforwardPlanUseCase.md` | 3 | ✓ Good |
| **Total** | **~2214** | Target: **~1500-1600** (30% reduction) |

---

## Phase 1: Extract Concerns From high-level.md

`high-level.md` is trying to be a summary AND the authoritative spec for 3 concerns that
have no other home. Fix: give them homes, then slim high-level down to a summary.

### 1A. Extract Health Monitoring → `doc/use-case/HealthMonitoring.md`

**What to move**: Lines 127-176 of `high-level.md` (the entire "Agent Health Monitoring" section
including the UseCase table, FailedToExecutePlanUseCase Detail, FailedToConvergeUseCase Detail).

**What stays in high-level.md**: A 3-line summary:
```markdown
## Agent Health Monitoring

Timeout + ping mechanism to detect crashed/hung agents. Four UseCase classes handle distinct
failure scenarios. See [Health Monitoring](use-case/HealthMonitoring.md) for the full spec.
```

**New doc structure** (`doc/use-case/HealthMonitoring.md`):
- Flow (4-step: timeout → ping → crash → recovery)
- UseCase table (NoStatusCallbackTimeOutUseCase, NoReplyToPingUseCase, FailedToExecutePlanUseCase, FailedToConvergeUseCase)
- FailedToExecutePlanUseCase detail
- FailedToConvergeUseCase detail

**Net effect**: high-level.md loses ~50 lines, new doc gains ~55 lines (with AP header).

### 1B. Extract DirectLLM → `doc/core/DirectLLM.md`

**What to move**: Lines 186-219 of `high-level.md` (entire "DirectLLM — Tier-Scoped Interfaces" section).

**What stays in high-level.md**: A 2-line summary:
```markdown
## DirectLLM — Tier-Scoped Interfaces

Interface-per-tier design (`DirectQuickCheapLLM`, `DirectBudgetHighLLM`). Not used for
iteration decisions. See [DirectLLM](core/DirectLLM.md) for tier assignments and contract.
```

**Net effect**: high-level.md loses ~33 lines, new doc gains ~38 lines.

### 1C. Consolidate Git Branch Naming into git.md

**What to move**: Lines 262-297 of `high-level.md` (the "Git Branch / Feature Naming" section
including Try-N Resolution). This content overlaps with `git.md` and `TicketShepherdCreator.md`.

The AP `ap.THL21SyZzJhzInG2m4zl2.E` (Git Branch / Feature Naming) should move to `git.md`,
which already owns `ap.BvNCIzjdHS2iAP4gAQZQf.E` (Git Commit Strategy). One doc for all git.

**What stays in high-level.md**: A 2-line summary:
```markdown
## Git

Branch naming, try-N resolution, and commit strategy are in
[Git](core/git.md) (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).
```

**What stays in TicketShepherdCreator.md**: Keep the existing reference ("Resolves try-N — ...
see ref.ap.THL21SyZzJhzInG2m4zl2.E").

**Net effect**: high-level.md loses ~36 lines. `git.md` gains ~20 lines (some content already
exists there — merge, don't duplicate). `git.md` goes from 148 → ~168 lines.

### 1D. Deduplicate Vocabulary

The vocabulary table (ShepherdServer, Agent, HandshakeGuid) appears in:
- `high-level.md` lines 9-17
- `agent-to-server-communication-protocol.md` lines 9-13
- `SpawnTmuxAgentSessionUseCase.md` lines 12-17

**Fix**: `high-level.md` keeps the authoritative vocabulary table. The other two docs replace
their vocabulary sections with: `> Vocabulary: see [high-level.md Vocabulary](../high-level.md#vocabulary)`.

**Net effect**: ~10 lines removed from protocol doc, ~6 lines from SpawnTmux.

### Phase 1 Result: high-level.md

**Before**: 360 lines
**After**: ~240 lines (summary doc with pointers — still the entry point, but no longer the overflow)

---

## Phase 2: Tighten Sub-Documents

### 2A. agent-to-server-communication-protocol.md — Extract UserQuestionHandler

**What to extract**: Lines 206-291 (UserQuestionHandler section: strategy interface, V1 UX
mockup, behavior table, future strategies table).

**New doc**: `doc/core/UserQuestionHandler.md` (~90 lines)

**What stays in protocol doc**: A 3-line summary:
```markdown
### User-Question — Handled via Strategy

Agent calls `callback_shepherd.user-question.sh`. Server delegates to
`UserQuestionHandler` (ref.ap.DvfGxWtvI1p6pDRXdHI2W.E) — see
[UserQuestionHandler](UserQuestionHandler.md) for the strategy interface and V1 behavior.
Answer delivered via TMUX `send-keys`.
```

**Net effect**: protocol doc loses ~85 lines (335 → ~250). New doc gains ~90 lines.

### 2B. agent-to-server-communication-protocol.md — Trim Server-Side Routing

Lines 169-193 re-explain the `SessionsState` → `CompletableDeferred` bridge. This is
`SessionsState.md`'s concern.

**Replace with**:
```markdown
### Server-Side Routing

On callback arrival, server looks up HandshakeGuid in `SessionsState`
(ref.ap.7V6upjt21tOoCFXA7nqNh.E), validates result against sub-part role, and completes
the `signalDeferred` (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E). See SessionsState spec for the
full bridge design.

Side-channel callbacks (`user-question`, `ping-ack`) update `lastActivityTimestamp`
but do NOT complete the deferred.
```

**Net effect**: protocol doc loses ~15 more lines (~250 → ~235).

### 2C. SessionsState.md — Remove Implementation Rationale

**Remove**: Lines 72-103 (entire "Concurrency Model" section — MutableSynchronizedMap rationale,
"Why Not ConcurrentHashMap").

**Replace with**:
```markdown
## Concurrency

Backed by coroutine-safe `MutableSynchronizedMap` (suspend-friendly mutex). V1's serial
execution makes per-operation synchronization sufficient. Revisit for V2 parallel agents.
```

**Net effect**: SessionsState.md loses ~28 lines (126 → ~100).

### 2D. PartExecutor.md — Collapse Flow Pseudocode

**DoerReviewerPartExecutor flow** (lines 148-178): Replace 18 numbered steps with 5 phases:
```markdown
### Flow (High-Level)

1. **Spawn doer** → register SessionEntry → send instructions → suspend on deferred
2. **On doer COMPLETED** → spawn reviewer → register → send instructions → suspend
3. **On reviewer PASS** → return `PartResult.Completed`
4. **On reviewer NEEDS_ITERATION** → check budget → create fresh deferred for doer →
   send new instructions to EXISTING doer TMUX session → loop to step 1 (doer suspend).
   On budget exceeded → `FailedToConvergeUseCase` → user decides continue or abort.
5. **On FailWorkflow / Crashed** → return corresponding `PartResult`
```

**SingleDoerPartExecutor flow** (lines 214-225): Replace 9 steps with:
```markdown
### SingleDoerPartExecutor

Spawn doer → register → send instructions → suspend on deferred → map AgentSignal to PartResult.
No reviewer, no iteration. Trivial subset of DoerReviewerPartExecutor.
```

**Net effect**: PartExecutor.md loses ~25 lines (238 → ~213).

### 2E. ContextForAgentProvider.md — Remove Request Data Classes

**Remove**: Lines 173-217 (the "Request Data Classes" section — 45 lines of Kotlin code).

The instruction content tables already define what each request contains. The data classes
are implementation that should match the tables — having both creates a dual-maintenance
burden in the spec.

**Replace with**:
```markdown
## Request Structure

Each method takes a typed request object matching the fields described in the tables above.
Request data classes are defined in the implementation alongside the interface.
```

**Net effect**: ContextForAgentProvider.md loses ~40 lines (235 → ~195).

### 2F. plan-and-current-state.md — Remove Duplicated Sections

**Remove "Iteration Semantics" section** (lines 234-258): This re-explains the doer→reviewer
loop that `PartExecutor.md` already owns. Replace with:
```markdown
## Iteration Semantics

See `DoerReviewerPartExecutor` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) for the full iteration
loop. The schema fields that support iteration (`iteration.max`, `iteration.current`,
`SubPartStatus` transitions) are defined in [SubPart Fields](#subpart-fields) above.
```

**Remove "PUBLIC.md Lifecycle" section** (lines 328-336): Duplicates `ai-out-directory.md`.
Replace with:
```markdown
## PUBLIC.md Lifecycle

See [ai-out-directory.md](ai-out-directory.md) (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) for
PUBLIC.md semantics (single file per sub-part, overwritten each iteration, tracked via git).
```

**Net effect**: plan-and-current-state.md loses ~30 lines (335 → ~305).

---

## Phase 3: Deduplicate V2 References

V2 resume (`ref.ap.LX1GCIjv6LgmM7AJFas20.E`) is mentioned in:
- `high-level.md` ×3
- `SessionsState.md` ×3
- `SpawnTmuxAgentSessionUseCase.md` ×1
- `plan-and-current-state.md` ×3

**Fix**: Keep ONE authoritative "V2 is out of scope" note in `high-level.md` (the "Harness-Level
Resume — V2" section at line 311). All other mentions replace multi-line V2 explanations with:
`(V2 — ref.ap.LX1GCIjv6LgmM7AJFas20.E)`.

Most instances are already short, but `SessionsState.md` has ~10 lines of V2 explanation that
can become single-line refs.

**Net effect**: ~15 lines total across multiple docs.

---

## Projected Result

| Doc | Before | After | Change |
|-----|-------:|------:|-------:|
| `doc/high-level.md` | 360 | ~240 | -120 |
| `doc/core/agent-to-server-communication-protocol.md` | 335 | ~235 | -100 |
| `doc/schema/plan-and-current-state.md` | 335 | ~305 | -30 |
| `doc/core/PartExecutor.md` | 238 | ~213 | -25 |
| `doc/core/ContextForAgentProvider.md` | 235 | ~195 | -40 |
| `doc/core/git.md` | 148 | ~168 | +20 |
| `doc/core/SessionsState.md` | 126 | ~100 | -26 |
| `doc/use-case/SpawnTmuxAgentSessionUseCase.md` | 96 | ~90 | -6 |
| **New: `doc/use-case/HealthMonitoring.md`** | — | ~55 | +55 |
| **New: `doc/core/DirectLLM.md`** | — | ~38 | +38 |
| **New: `doc/core/UserQuestionHandler.md`** | — | ~90 | +90 |
| Other docs (unchanged) | 242 | 242 | 0 |
| **Total** | **~2214** | **~1971** | **~-243 (11% net reduction)** |

The raw line reduction is modest because we're extracting, not deleting. The real wins:

1. **high-level.md drops from 360 → 240** — now a scannable summary
2. **Top 3 docs drop from 360/335/335 → 240/235/305** — more uniform sizes
3. **Cross-document duplication eliminated** — each concept has one home
4. **Implementation detail removed from spec** — concurrency rationale, data classes, pseudocode

---

## Execution Order

| Gate | What | Verify | Risk if skipped |
|------|------|--------|-----------------|
| **G1** | Phase 1: Restructure high-level.md (extractions + dedup vocabulary) | high-level.md reads as summary; new docs compile; all AP refs resolve | High — high-level is the root doc, everything chains from it |
| **G2** | Phase 2A-2B: Slim protocol doc | Protocol doc is ~235 lines; UserQuestionHandler has its own doc; no broken refs | Medium — protocol doc is the second-most-read |
| **G3** | Phase 2C-2F: Tighten remaining docs | Each doc focuses on its concern; no cross-doc duplication of flows/semantics | Low — these are independent, can be done in any order |
| **G4** | Phase 3: V2 reference cleanup | All V2 mentions are single-line refs except the one authoritative section | Low — cosmetic but improves scannability |
| **G5** | Update `1_core_description.md` (auto-loaded CLAUDE.md summary) and Linked Documentation table | CLAUDE.md summary matches new doc structure | Medium — stale summary misleads sub-agents |

### Verification at Each Gate

After each gate, run:
```bash
# 1. All APs still resolve (no broken anchor points)
for ap in $(grep -roh 'ap\.[A-Za-z0-9]\+\.E' doc/ | sort -u); do
  count=$(grep -rl "$ap" doc/ | wc -l)
  echo "$ap: $count references"
done

# 2. All internal links resolve
# (manual check — verify each [text](path.md) link target exists)

# 3. Line counts match expectations
wc -l doc/**/*.md doc/**/**/*.md 2>/dev/null | sort -rn
```
