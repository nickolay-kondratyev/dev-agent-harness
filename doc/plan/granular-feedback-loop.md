# Plan: Granular Feedback Loop — Individual Feedback Items with Severity-Based Processing

## ap.5Y5s8gqykzGN1TVK5MZdS.E

---

## Approach Summary

Replace the current "dump all reviewer feedback at once" pattern with a **granular feedback loop**
that feeds individual feedback items to the doer one at a time. Feedback is written as separate
markdown files by the reviewer into a severity-stratified `__feedback/` directory tree at the
part level. The harness drives an **inner loop** within the existing iteration cycle — feeding
critical items first, then important, then optional — and checks for self-compaction
(ref.ap.8nwz2AHf503xwq8fKuLcl.E) between items. This gives tighter control over context window
usage and reduces the risk of mid-task interrupts.

**Key semantic:** Feeding individual feedback items does NOT count as an iteration. An iteration
counter increments only when the full cycle completes: reviewer → doer(all items) → reviewer.
This preserves the existing `iteration.current` / `iteration.max` semantics
(ref.ap.56azZbk7lAMll0D4Ot2G0.E).

**Why this over the current design:**
- Current: reviewer writes all feedback to PUBLIC.md → doer receives everything → context
  overloads on complex reviews. No natural self-compaction boundaries during re-work.
- Proposed: one item at a time → done boundary after each → compaction opportunity between items.
  Doer's context stays focused. Harness retains control.

---

## Decisions (Resolved)

### D1: Who produces individual feedback files?

**Decided: Reviewer writes them directly.** The reviewer is instructed to write individual `.md`
files to `__feedback/unaddressed/{severity}/`. The harness does NOT parse PUBLIC.md into
individual files — that would violate the existing principle "harness does not parse markdown
structure" (ref.ap.EslyJMFQq8BBrFXCzYw5P.E enforcement boundary).

### D2: Does PUBLIC.md still exist for reviewers?

**Decided: Yes.** PUBLIC.md remains the reviewer's work log (overall verdict, what was reviewed,
what passed). Individual actionable issues go to `__feedback/unaddressed/` as separate files.
This separates "what I observed" from "what needs fixing" and keeps the existing PUBLIC.md
contract intact for downstream visibility (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E visibility rules).

### D3: PUBLIC.md during the inner feedback loop

**Decided: PUBLIC.md persists from the doer's first pass.** The doer appends a brief one-liner
per addressed item (e.g., `- Addressed: <item-summary>`). Detailed work logs for each item
live in the feedback file itself (movement record). The existing shallow validation (exists +
non-empty, ref.ap.THDW9SHzs1x2JN9YP9OYU.E) passes because PUBLIC.md already exists from
the doer's first pass.

### D4: Blocking rules by severity

**Decided: Critical + Important block. Optional does not.**
- `critical` and `important` items in `unaddressed/` MUST be processed (moved to `addressed/`
  or `rejected/`) before the inner loop ends.
- `optional` items can remain in `unaddressed/` — the doer can skip them.
- Harness enforces: part cannot complete while `unaddressed/critical/` or `unaddressed/important/`
  contain files. This is a hard constraint, not reviewer guidance.

### D5: Iteration counter semantics

**Decided: No change.** `iteration.current` still increments when the reviewer signals
`needs_iteration` (ref.ap.56azZbk7lAMll0D4Ot2G0.E). The inner feedback loop is the detail
of what happens within one iteration. Individual feedback item processing does not increment
the counter.

### D6: Who moves files between feedback directories?

**Decided: The agents move files. Harness validates.** The doer moves files from `unaddressed/`
to `addressed/` or `rejected/`, adding a movement record inside the file. The reviewer can move
files from `rejected/` back to `unaddressed/`, also with a record. The harness validates the
result (e.g., "was the file moved?") but does not perform the moves.

---

## New Concept: `__feedback/` Directory — ap.3Hskx3JzhDlixTnvYxclk.E

### Directory Tree

Lives at the **part level** in `.ai_out/`, alongside sub-part directories. Shared between the
doer and reviewer within a part.

```
.ai_out/${git_branch}/execution/${part_name}/
├── __feedback/
│   ├── unaddressed/
│   │   ├── critical/           # Must be processed before leaving the part
│   │   ├── important/          # Must be processed before leaving the part
│   │   └── optional/           # Doer can skip — does not block part completion
│   ├── addressed/
│   │   ├── critical/
│   │   ├── important/
│   │   └── optional/
│   └── rejected/               # Reviewer can move back to unaddressed/ on next pass
│       ├── critical/
│       ├── important/
│       └── optional/
├── ${doer_sub_part}/           # e.g., impl
│   └── comm/...
└── ${reviewer_sub_part}/       # e.g., review
    └── comm/...
```

### Feedback File Format

Each feedback item is a standalone markdown file. Named by the reviewer with a descriptive slug
(e.g., `missing-null-check-in-parser.md`).

```markdown
# <issue title>

**File(s):** `<path/to/affected/file.kt>`

<Detailed description of the issue and what needs to change>

---

## Movement Log

<!-- Appended by whoever moves this file between directories -->
```

### Movement Record Format

When a file is moved between directories, the mover appends a record to the `## Movement Log`
section:

```markdown
### [YYYY-MM-DDTHH:MM:SSZ] Moved by: <role> | From: <source_dir> → To: <target_dir>
<brief justification — WHY this was addressed/rejected/re-opened>
```

Example — doer addresses an item:
```markdown
### [2026-03-14T15:30:00Z] Moved by: impl | From: unaddressed/critical → To: addressed/critical
Fixed by adding null check in Parser.kt:45. Also added unit test in ParserTest.kt.
```

Example — reviewer re-opens a rejected item:
```markdown
### [2026-03-14T16:00:00Z] Moved by: review | From: rejected/important → To: unaddressed/important
Rejection reasoning doesn't account for the edge case in concurrent access. Re-opening.
```

### Creation Timing

- `__feedback/` directories are created by the harness (`AiOutputStructure`) when setting up the
  part's directory structure (ref.ap.BXQlLDTec7cVVOrzXWfR7.E). All nine leaf directories
  (3 statuses × 3 severities) exist before the first agent runs.
- Feedback files are NOT created at init — only when the reviewer writes them on `needs_iteration`.

### Scoping Rules

- `__feedback/` exists at the **part** level, not sub-part level. Both doer and reviewer
  within the same part read/write to it.
- `__feedback/` is NOT visible to agents in other parts. `ContextForAgentProvider`
  (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) does not include `__feedback/` contents in cross-part
  visibility (it's an intra-part communication channel).

---

## Inner Feedback Loop — DoerReviewerPartExecutor Change

### Updated Flow (extends ref.ap.mxIc5IOj6qYI7vgLcpQn5.E)

The existing DoerReviewerPartExecutor steps 1–3 are unchanged. Step 4 (On reviewer
NEEDS_ITERATION) gains an inner loop:

```
4. On reviewer NEEDS_ITERATION:
   a. PUBLIC.md validation (existing)
   b. Late fail-workflow checkpoint (existing)
   c. Check budget: iteration.current >= iteration.max → FailedToConvergeUseCase (existing)
   d. GitCommitStrategy.onSubPartDone (existing — captures reviewer's output + feedback files)
   e. Increment iteration.current (existing)
   e2. ── FEEDBACK FILES PRESENCE GUARD (NEW) ──
      Validate: at least one file exists in unaddressed/critical/ OR unaddressed/important/
                OR unaddressed/optional/
      ├─ If files exist → proceed to inner loop
      └─ If ALL empty → reviewer said needs_iteration but wrote no feedback files.
         Re-instruct reviewer: "You signaled needs_iteration but wrote no feedback files
         to __feedback/unaddressed/. Write individual feedback files (one per issue) to the
         appropriate severity directory, then re-signal needs_iteration."
         ├─ Await reviewer re-signal (one retry)
         ├─ On needs_iteration with files → proceed to inner loop
         ├─ On needs_iteration with still no files → PartResult.AgentCrashed(
         │   "Reviewer signaled needs_iteration twice without writing feedback files")
         └─ On pass → proceed to step 3 (reviewer changed its mind)
   f. ── INNER FEEDBACK LOOP (NEW) ──
      │
      ├─ List feedback files in unaddressed/critical/ (sorted by filename)
      │   For each file:
      │   ├─ Self-compaction check at done boundary (ref.ap.8nwz2AHf503xwq8fKuLcl.E)
      │   ├─ Assemble doer instructions with THIS ONE feedback item
      │   ├─ Deliver via AckedPayloadSender to existing doer session (re-instruction pattern)
      │   ├─ Await doer done signal (health-aware await loop)
      │   ├─ PUBLIC.md validation (shallow — exists, non-empty; doer appends one-liner)
      │   ├─ Validate: feedback file moved out of unaddressed/critical/
      │   │   ├─ If moved → GitCommitStrategy.onSubPartDone
      │   │   └─ If NOT moved → re-instruction (one retry, then PartResult.AgentCrashed)
      │   └─ (next file)
      │
      ├─ List feedback files in unaddressed/important/ (sorted by filename)
      │   (same per-file flow as critical)
      │
      ├─ List feedback files in unaddressed/optional/ (sorted by filename)
      │   For each file:
      │   ├─ Self-compaction check at done boundary
      │   ├─ Assemble doer instructions with THIS ONE feedback item
      │   │   (instructions note: "This is OPTIONAL. Address if worthwhile, or signal done to skip.")
      │   ├─ Deliver via AckedPayloadSender to existing doer session
      │   ├─ Await doer done signal
      │   ├─ PUBLIC.md validation (shallow)
      │   ├─ Check: file moved?
      │   │   ├─ If moved → GitCommitStrategy.onSubPartDone
      │   │   └─ If NOT moved → OK (optional — doer chose to skip). No error. No commit.
      │   └─ (next file)
      │
      └─ END INNER LOOP
   g. Validate: unaddressed/critical/ is empty AND unaddressed/important/ is empty
      ├─ If not empty → BUG — all critical/important should have been processed.
      │   PartResult.AgentCrashed("Critical/important feedback not processed after inner loop")
      └─ If empty → proceed
   h. Assemble reviewer instructions (includes __feedback/addressed/ and __feedback/rejected/ contents)
   i. Deliver to reviewer (re-instruction pattern)
   j. Await reviewer done signal → go to step 3 (PASS) or step 4 (NEEDS_ITERATION)
```

### Connection to Self-Compaction (ref.ap.8nwz2AHf503xwq8fKuLcl.E)

The inner feedback loop creates **frequent done boundaries** — one after each feedback item.
Each done boundary is a natural soft-compaction checkpoint. The harness reads
`context_window_slim.json` after every item and compacts before feeding the next if the
threshold is crossed.

This is a significant improvement over the current design where the doer processes ALL
reviewer feedback in one go. In the current design:
- If the doer runs out of context mid-re-work → emergency interrupt (Ctrl+C), forced
  compaction, session rotation, agent must resume from PRIVATE.md
- The emergency path is disruptive and risks lost work

With the inner feedback loop:
- Soft compaction triggers between items while the agent has ample room to summarize
- The agent finishes one item, compacts, starts fresh on the next
- Emergency interrupts become rare — the soft threshold (65% remaining) catches most cases

**The inner loop makes self-compaction proactive rather than reactive.**

### Doer Instructions Per Feedback Item

The doer receives a focused instruction per item:

```markdown
## Feedback Item to Address

<contents of the feedback markdown file>

## Instructions

1. Address the feedback item above in the codebase.
2. When done, move the feedback file from its current location to the corresponding
   `addressed/{severity}/` directory. Add a movement record (see Movement Log format below).
3. If you disagree with this feedback, move it to `rejected/{severity}/` instead, with
   a WHY-NOT justification in the movement record (ref.ap.kmiKk7vECiNSpJjAXYMyE.E).
4. Update your PUBLIC.md with a brief one-liner noting this item was addressed/rejected.
5. Signal done: `callback_shepherd.signal.sh done completed`

### Movement Log Format
<static text with the movement record template>

### Current feedback file path
`<absolute path to the file in unaddressed/{severity}/>`

### Target paths
- Addressed: `<absolute path to addressed/{severity}/>`
- Rejected: `<absolute path to rejected/{severity}/>`
```

For optional items, add:
```markdown
**This feedback is OPTIONAL.** You may choose to skip it. If skipping, simply signal done
without moving the file. The feedback will remain in unaddressed/ but will NOT block
part completion.
```

### Reviewer Instructions — Updated

On subsequent iterations (after inner loop), the reviewer's instruction file
(assembled by `ContextForAgentProvider` ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) includes:

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 7a | **Addressed feedback** | `__feedback/addressed/{severity}/*.md` | What the doer addressed — verify fixes are correct |
| 7b | **Rejected feedback** | `__feedback/rejected/{severity}/*.md` | What the doer rejected — review WHY-NOT reasoning. May move back to `unaddressed/` if disagreeing. |
| 7c | **Skipped optional feedback** | `__feedback/unaddressed/optional/*.md` | Optional items doer chose not to address — reviewer can accept or escalate to `important` by moving. |
| 7d | **Feedback writing instructions** | Static text | How to write new feedback files to `__feedback/unaddressed/{severity}/` |

The reviewer is instructed:
- Write **new** feedback to `__feedback/unaddressed/{severity}/` (one file per issue)
- Review `rejected/` items — if disagree with rejection, move back to `unaddressed/` with record
- May reclassify severity (e.g., move from `rejected/optional/` to `unaddressed/important/`)
- Signal `pass` only if no `critical` or `important` items remain in `unaddressed/`
- Signal `needs_iteration` if there are items to process

### Part Completion Guard

The harness enforces a **hard constraint** before allowing `PartResult.Completed`:

```
On reviewer PASS:
  1. PUBLIC.md validation (existing)
  2. Late fail-workflow checkpoint (existing)
  3. NEW: Validate __feedback/unaddressed/critical/ is empty
  4. NEW: Validate __feedback/unaddressed/important/ is empty
  5. If 3 or 4 fails → do NOT complete. Log ERROR.
     Re-instruct reviewer: "You signaled pass but there are unaddressed critical/important
     feedback items. Review them and either move to rejected/ (with justification) or
     signal needs_iteration."
     Await reviewer's re-signal. (One retry, then PartResult.AgentCrashed.)
  6. Optional items in unaddressed/optional/ → acceptable. Part completes.
```

---

## Spec Documents Requiring Updates

| Document | Change | Scope |
|----------|--------|-------|
| `doc/schema/ai-out-directory.md` (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) | Add `__feedback/` directory tree at part level. Add feedback file format. Update directory tree diagram. | Directory schema |
| `doc/core/PartExecutor.md` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) | Add inner feedback loop to DoerReviewerPartExecutor flow (step 4). Add part completion guard. Reference self-compaction synergy. | Executor logic |
| `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) | Add per-feedback-item doer instruction assembly. Update reviewer instruction concatenation table (sections 7a–7d). Update structured feedback contract (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) — reviewer now writes individual files instead of inline issues in PUBLIC.md. | Instruction assembly |
| `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E) | Add section on inner feedback loop as frequent done-boundary source. Note that granular feedback loop reduces emergency compaction frequency. Cross-reference ref.ap.5Y5s8gqykzGN1TVK5MZdS.E. | Self-compaction synergy |
| `doc/high-level.md` | Update "Sub-Part Transitions" section — reviewer-driven iteration now includes inner feedback loop. Add link to this spec. | High-level overview |
| `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E) | Clarify that iteration.current semantics are unchanged — inner loop does not increment. No schema change needed. | Schema clarification |

---

## Requirements

### R1: `__feedback/` Directory Structure in `.ai_out/`
- Nine leaf directories created at part setup: 3 statuses (`unaddressed`, `addressed`, `rejected`)
  × 3 severities (`critical`, `important`, `optional`)
- Lives at `execution/${part_name}/__feedback/`, not under sub-parts
- Created by `AiOutputStructure.ensureStructure()` (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
- Empty at creation — no placeholder files
- Verifiable: directory creation test; `ls -R` shows all nine leaf directories

### R2: Reviewer Writes Individual Feedback Files to `__feedback/unaddressed/{severity}/`
- One markdown file per actionable issue, named with descriptive slug
- Reviewer instructions updated to include file format and directory paths
- Reviewer's PUBLIC.md remains the work log (verdict, what passed, overall assessment)
- Structured feedback contract (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) updated: issues go to
  individual files, not inline in PUBLIC.md
- Verifiable: reviewer instructions contain __feedback/ paths and file format guidance

### R3: Inner Feedback Loop in DoerReviewerPartExecutor
- After reviewer `needs_iteration`: iterate through `unaddressed/critical/`, then
  `unaddressed/important/`, then `unaddressed/optional/`
- Feed ONE feedback file at a time to the doer via re-instruction pattern
- Doer signals `done completed` after each item
- Critical/important: validate file moved out of `unaddressed/` (retry once, then AgentCrashed)
- Optional: file may remain in `unaddressed/` (doer chose to skip — no error)
- Self-compaction check (ref.ap.8nwz2AHf503xwq8fKuLcl.E) at each done boundary
- Git commit after each addressed/rejected item
- Verifiable: unit test with mocked filesystem — items processed in severity order,
  critical/important enforced, optional skippable

### R4: Doer Per-Item Instructions via ContextForAgentProvider
- New method or parameterization in `SubPartInstructionProvider`
  (ref.ap.4c6Fpv6NjecTyEQ3qayO5.E): assemble instructions for a single feedback item
- Includes: feedback file content, movement instructions, target paths, PUBLIC.md one-liner guidance
- Optional items include skip guidance
- Verifiable: unit test — instruction content includes exactly one feedback item

### R5: Movement Record in Feedback Files
- When an agent moves a file, it appends a record to `## Movement Log` section
- Record includes: timestamp, mover role, source → target directories, justification
- Harness does NOT validate record format (same KISS principle as PUBLIC.md format —
  ref.ap.EslyJMFQq8BBrFXCzYw5P.E enforcement boundary)
- Verifiable: agent instructions include movement record format template

### R6: Reviewer Instructions Include `__feedback/` State
- On iteration > 1: reviewer sees `addressed/`, `rejected/`, and remaining `unaddressed/optional/`
- Reviewer can move `rejected/` items back to `unaddressed/` with movement record
- Reviewer can reclassify severity (e.g., move from `rejected/optional/` to `unaddressed/important/`)
- Reviewer writes NEW feedback to `unaddressed/{severity}/` for new issues
- Verifiable: reviewer instruction concatenation includes feedback directory contents

### R7: Part Completion Guard — No Unaddressed Critical/Important
- After reviewer `PASS` + PUBLIC.md validation: harness checks `unaddressed/critical/` and
  `unaddressed/important/` are empty
- If not empty: re-instruct reviewer (one retry, then AgentCrashed)
- `unaddressed/optional/` may contain files — does not block completion
- Verifiable: unit test — PASS with unaddressed critical → re-instruction → eventual
  completion or failure

### R8: Feedback Files Presence Guard on `needs_iteration`
- After reviewer signals `needs_iteration`: harness checks that at least one file exists in
  `unaddressed/` (any severity)
- If empty: re-instruct reviewer to write feedback files (one retry)
- If still empty after retry: `PartResult.AgentCrashed`
- If reviewer changes verdict to `pass` on retry: proceed to completion (step 3)
- Verifiable: unit test — needs_iteration with empty unaddressed → re-instruction → eventual
  file creation or failure

### R9: Iteration Counter Unchanged
- `iteration.current` increments when reviewer signals `needs_iteration` — same as now
- Inner loop processing does NOT increment the counter
- `iteration.max` budget applies to reviewer passes, not individual feedback items
- Verifiable: unit test — multiple items in inner loop, counter increments once

---

## Implementation Gates

### Gate 1: Directory Structure + File Format Spec
**Scope:** R1, R5
**What:** `__feedback/` directory tree in `AiOutputStructure`. Feedback file format and
movement record format documented.
**Verify:**
- Unit test: `ensureStructure()` creates all nine leaf directories
- Spec review: file format and movement record documented in ai-out-directory.md
**Proceed when:** Directory structure is created reliably. Format documented.

### Gate 2: Reviewer Contract Update
**Scope:** R2, R6
**What:** Reviewer instructions updated to write individual feedback files. Reviewer
instructions on iteration > 1 include `__feedback/` state. Structured feedback contract
(ref.ap.EslyJMFQq8BBrFXCzYw5P.E) updated.
**Verify:**
- Unit test: reviewer instructions contain `__feedback/` paths, file format, severity guidance
- Unit test: iteration > 1 instructions include `addressed/`, `rejected/`, `unaddressed/optional/`
**Proceed when:** Reviewer can be instructed to write individual feedback files and review
prior iteration's results.

### Gate 3: Inner Feedback Loop + Doer Instructions + Guards
**Scope:** R3, R4, R8, R9
**What:** Inner loop in DoerReviewerPartExecutor. Per-item doer instructions via
ContextForAgentProvider. Iteration counter unchanged.
**Verify:**
- Unit test: inner loop processes critical → important → optional in order
- Unit test: critical/important items enforce file movement
- Unit test: optional items allow skip
- Unit test: self-compaction check fires at each done boundary
- Unit test: iteration.current increments once per reviewer needs_iteration, not per item
- Unit test: needs_iteration with empty unaddressed → re-instruction → reviewer writes files → loop proceeds
- Unit test: needs_iteration with empty unaddressed after retry → AgentCrashed
**Proceed when:** Full inner loop works with mocked agents and filesystem, including guards.

### Gate 4: Part Completion Guard
**Scope:** R7
**What:** Harness validates no critical/important in `unaddressed/` before completing.
**Verify:**
- Unit test: PASS with empty unaddressed/critical + unaddressed/important → Completed
- Unit test: PASS with files in unaddressed/critical → re-instruction → eventual handling
- Unit test: PASS with files in unaddressed/optional → Completed (optional doesn't block)
**Proceed when:** Part completion is correctly guarded.

### Gate 5: Integration Validation
**Scope:** All requirements end-to-end
**What:** Integration test with real agent (or GLM fake) confirming the full feedback loop.
**Verify:**
- Reviewer writes individual feedback files
- Doer receives one item at a time, addresses/rejects each
- Self-compaction triggers between items when context is low
- Iteration counter behaves correctly
- Part completion guard works
**Proceed when:** Full flow validated with real or realistic agent.

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Reviewer writes monolithic PUBLIC.md instead of individual files** | Inner loop has nothing to process; iteration stalls | Harness-enforced guard (R8): if `needs_iteration` but `unaddressed/` is empty → re-instruct reviewer to write feedback files. One retry, then AgentCrashed. |
| **Too many feedback files overwhelm the inner loop** | Many small items means many done→compaction cycles; overhead accumulates | Each cycle is lightweight (one file read, one instruction assembly). 20 items × 2 min each = 40 min — comparable to current single-pass behavior but with better context management. |
| **Severity classification is subjective** | Reviewer calls something "optional" that the doer thinks is "critical," or vice versa | Severity determines processing order and skip rules, not quality. The reviewer is the severity authority (consistent with "reviewer's verdict is authoritative" principle). Doer can reject critical items with justification. |
| **Movement by agent fails (wrong path, forgot to move)** | File stays in `unaddressed/` after doer signals done | Harness validates file was moved. One retry (re-instruct with explicit path). Then AgentCrashed. |
| **Reviewer moves ALL rejected items back to unaddressed** | Creates an infinite loop — every iteration re-processes the same items | `iteration.max` budget still applies. FailedToConvergeUseCase triggers when budget is exceeded. The reviewer is responsible for accepting reasonable rejections. |

---

## Resolved Questions

1. **Feedback file naming collisions:** Trust the reviewer to use unique names. Simpler than
   iteration-prefix subdirectories. Same trust model as expecting good feedback content.

2. **Planning phase:** Yes — `__feedback/` applies to PLANNER↔PLAN_REVIEWER. Falls out from
   the shared `PartExecutor` (DRY, ref.ap.fFr7GUmCYQEV5SJi8p6AS.E). Planning feedback lives
   at `planning/__feedback/`.

3. **Cross-iteration feedback history:** Keep cumulative. `addressed/` and `rejected/` are NOT
   cleared between iterations. The reviewer references prior items. Git history tracks everything.

4. **`needs_iteration` with no feedback files:** Harness-enforced guard. If reviewer signals
   `needs_iteration` but `unaddressed/` is empty across all severities → re-instruct reviewer
   to write feedback files. One retry, then `AgentCrashed`. See R8.
