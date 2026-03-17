# Plan: Granular Feedback Loop — Individual Feedback Items with Harness-Driven Processing

## ap.5Y5s8gqykzGN1TVK5MZdS.E

---

## Approach Summary

Replace the current "dump all reviewer feedback at once" pattern with a **granular feedback loop**
that feeds individual feedback items to the doer one at a time. Feedback is written as separate
markdown files by the reviewer into `__feedback/pending/` at the part level. The harness drives an
**inner loop** within the existing iteration cycle — feeding critical items first, then important,
then optional — and checks for self-compaction (ref.ap.8nwz2AHf503xwq8fKuLcl.E) between items.

**Harness owns all file movement.** Agents never move feedback files between directories. The doer
writes a `## Resolution: ADDRESSED` or `## Resolution: REJECTED` marker in the feedback file. The
harness reads this marker after `done` and moves the file to `addressed/` or `rejected/`
accordingly. This eliminates the fragile agent-driven file movement pattern and all its associated
validation/retry/crash paths.

**Per-item rejection negotiation — focused resolution.** When the doer rejects a feedback item,
the disagreement is resolved **immediately at the point of contention** — while both agents have
the specific item in full context. The harness sends the rejection (with the doer's reasoning)
to the reviewer for judgment. The reviewer either accepts (`done pass`) or insists
(`done needs_iteration`). If the reviewer insists, the item goes back to the doer with the
reviewer's counter-reasoning. At most **2 rounds of disagreement** per item — after that, the
doer must comply (reviewer is authority).

**Why inline negotiation, not deferred to the next outer iteration:** Focus. If a rejection is
deferred, the doer has moved on and lost context about why it rejected; the reviewer has to
re-read everything and re-file. The back-and-forth reasoning is most valuable when both agents
are actively focused on the specific item. This is encapsulated as
`RejectionNegotiationUseCase` (ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E) — a self-contained sub use
case with its own test surface via `FakeAgentFacade`.

**Key semantic:** Feeding individual feedback items does NOT count as an iteration. An iteration
counter increments only when the full cycle completes: reviewer → doer(all items) → reviewer.
This preserves the existing `iteration.current` / `iteration.max` semantics
(ref.ap.56azZbk7lAMll0D4Ot2G0.E).

**Why this over the current design:**
- Current: reviewer writes all feedback to PUBLIC.md → doer receives everything → context
  overloads on complex reviews. No natural self-compaction boundaries during re-work.
- Proposed: one item at a time → done boundary after each → compaction opportunity between items.
  Doer's context stays focused. Harness retains control. Rejections resolved immediately.

---

## Decisions (Resolved)

### D1: Who produces individual feedback files?

**Decided: Reviewer writes them directly.** The reviewer is instructed to write individual `.md`
files to `__feedback/pending/`. The harness does NOT parse PUBLIC.md into individual files — that
would violate the existing principle "harness does not parse markdown structure"
(ref.ap.EslyJMFQq8BBrFXCzYw5P.E enforcement boundary).

**Severity is encoded in the filename prefix** — not in subdirectories. Format:
`{severity}__{descriptive-slug}.md` (e.g., `critical__missing-null-check-in-parser.md`). The
harness reads the prefix to determine processing order. Three valid prefixes: `critical__`,
`important__`, `optional__`.

### D2: Does PUBLIC.md still exist for reviewers?

**Decided: Yes.** PUBLIC.md remains the reviewer's work log (overall verdict, what was reviewed,
what passed). Individual actionable issues go to `__feedback/pending/` as separate files.
This separates "what I observed" from "what needs fixing" and keeps the existing PUBLIC.md
contract intact for downstream visibility (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E visibility rules).

### D3: PUBLIC.md during the inner feedback loop

**Decided: PUBLIC.md persists from the doer's first pass.** The doer appends a brief one-liner
per addressed item (e.g., `- Addressed: <item-summary>`). The `## Resolution` section in each
feedback file captures the detailed reasoning. The existing shallow validation (exists +
non-empty, ref.ap.THDW9SHzs1x2JN9YP9OYU.E) passes because PUBLIC.md already exists from
the doer's first pass.

### D4: Blocking rules by severity

**Decided: Critical + Important block. Optional does not.**
- `critical` and `important` items in `pending/` MUST be processed before the inner loop ends.
- `optional` items can be skipped by the doer.
- Harness enforces: part cannot complete while `pending/` contains `critical__*` or
  `important__*` files. This is a hard constraint, not reviewer guidance.

### D5: Iteration counter semantics

**Decided: No change.** `iteration.current` still increments when the reviewer signals
`needs_iteration` (ref.ap.56azZbk7lAMll0D4Ot2G0.E). The inner feedback loop is the detail
of what happens within one iteration. Individual feedback item processing does not increment
the counter.

### D6: Who moves files between feedback directories?

**Decided: Harness moves files. Agents never move files.**

The doer writes a `## Resolution: ADDRESSED` or `## Resolution: REJECTED` section in the
feedback file itself. After the doer signals `done`, the harness reads the resolution marker
and moves the file:
- `ADDRESSED` → `addressed/`
- `REJECTED` → triggers per-item rejection negotiation (see below), then `rejected/` if accepted

If the resolution marker is missing after `done`, the harness re-instructs the doer to write
it (one retry, then `AgentCrashed`).

**Why harness-owned movement:** Agents are unreliable at file operations (wrong paths, forgetting
to move). The harness is a Kotlin program that will reliably move a file. Shifting file movement
to the reliable actor eliminates an entire class of failure modes and their associated
validation/retry/crash paths.

### D7: Rejection negotiation — bounded at 2 rounds, resolved at point of contention

**Decided: At most 2 rounds of disagreement per item. Reviewer is authority.**

**Why inline, not deferred:** When a doer rejects a feedback item, that is the moment of
**maximum focus** — both agents have the specific item, the code context, and the reasoning
fresh. Deferring to the next outer iteration means:
- Doer has moved on and lost context about why it rejected
- Reviewer must re-read everything and re-file as a new feedback item
- The back-and-forth reasoning chain is broken across iterations

Resolving immediately preserves focus and produces higher-quality outcomes.

**Encapsulation:** This flow is extracted as `RejectionNegotiationUseCase`
(ap.fvpIuw4Yeeq1IXDvLC3mL.E) — a self-contained sub use case called by
`PartExecutorImpl` during the inner feedback loop. It receives the
`AgentFacade`, both agent handles (doer + reviewer), and the feedback file. It returns
a `RejectionResult` (`Accepted` → file to `rejected/`, `AddressedAfterInsistence` →
file to `addressed/`, `AgentCrashed` → propagate). Fully testable via `FakeAgentFacade`
+ virtual time — each negotiation scenario is an independent unit test.

When the doer rejects a feedback item:
1. Harness sends the rejection + doer's reasoning to the reviewer
2. Reviewer signals `done pass` (accept rejection) or `done needs_iteration` (insist)
3. If reviewer insists: item goes back to doer with counter-reasoning. **Round 1.**
4. If doer addresses it → done. If doer rejects again → back to reviewer. **Round 2.**
5. If reviewer insists a second time → doer MUST address. Third rejection →
   `PartResult.AgentCrashed` (fundamentally broken agent).

This reuses existing signal semantics:
- Reviewer `done pass` = "rejection is reasonable, move on"
- Reviewer `done needs_iteration` = "must be addressed, sending back"

---

## `__feedback/` Directory — ap.3Hskx3JzhDlixTnvYxclk.E

### Directory Tree

Lives at the **part level** in `.ai_out/`, alongside sub-part directories. Shared between the
doer and reviewer within a part.

```
.ai_out/${git_branch}/execution/${part_name}/
├── __feedback/
│   ├── pending/              # Reviewer writes feedback files here
│   ├── addressed/            # Harness moves here after doer addresses
│   └── rejected/             # Harness moves here after reviewer accepts rejection
├── ${doer_sub_part}/         # e.g., impl
│   └── comm/...
└── ${reviewer_sub_part}/     # e.g., review
    └── comm/...
```

**3 directories** — down from 9 in the previous design. Severity is encoded in filename
prefixes, not directory structure.

### Feedback File Format

Each feedback item is a standalone markdown file. Named by the reviewer with a severity prefix
and descriptive slug: `{severity}__{descriptive-slug}.md`.

```markdown
# <issue title>

**File(s):** `<path/to/affected/file.kt>`

<Detailed description of the issue and what needs to change>
```

**Valid severity prefixes:** `critical__`, `important__`, `optional__`.

### Resolution Marker Format

After processing a feedback item, the doer appends a resolution section to the file:

```markdown
## Resolution: ADDRESSED
Added null check in Parser.kt:45. Also added unit test in ParserTest.kt.
```

or:

```markdown
## Resolution: REJECTED
This null check is unnecessary — the upstream caller already validates non-null
at InputValidator.kt:32. Adding a redundant check would violate DRY and mislead
future readers into thinking the upstream validation is insufficient.
```

The harness reads the `## Resolution:` line to determine disposition. Parsing is minimal —
scan for `## Resolution: ADDRESSED` or `## Resolution: REJECTED` (case-insensitive match
on the keyword after `## Resolution:`). Any other value or missing marker → re-instruct doer.

### Creation Timing

- `__feedback/` directories (`pending/`, `addressed/`, `rejected/`) are created by the harness
  (`AiOutputStructure`) when setting up the part's directory structure
  (ref.ap.BXQlLDTec7cVVOrzXWfR7.E). All three directories exist before the first agent runs.
- Feedback files are NOT created at init — only when the reviewer writes them on `needs_iteration`.

### Scoping Rules

- `__feedback/` exists at the **part** level, not sub-part level. Both doer and reviewer
  within the same part read/write to it.
- `__feedback/` is NOT visible to agents in other parts. `ContextForAgentProvider`
  (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) does not include `__feedback/` contents in cross-part
  visibility (it's an intra-part communication channel).

---

## Inner Feedback Loop — PartExecutorImpl Change

### Updated Flow (extends ref.ap.mxIc5IOj6qYI7vgLcpQn5.E)

The existing PartExecutorImpl steps 1–3 are unchanged. Step 4 (On reviewer
NEEDS_ITERATION) gains an inner loop:

```
4. On reviewer NEEDS_ITERATION:
   a. PUBLIC.md validation (existing)
   b. Check budget: iteration.current >= iteration.max → FailedToConvergeUseCase (existing)
   d. GitCommitStrategy.onSubPartDone (existing — captures reviewer's output + feedback files)
   e. Increment iteration.current (existing)
   e2. ── FEEDBACK FILES PRESENCE GUARD ──
      Validate: at least one file exists in __feedback/pending/
      ├─ If files exist → proceed to inner loop
      └─ If empty → reviewer said needs_iteration but wrote no feedback files.
         Re-instruct reviewer: "You signaled needs_iteration but wrote no feedback files
         to __feedback/pending/. Write individual feedback files (one per issue) with
         severity prefix, then re-signal needs_iteration."
         ├─ Await reviewer re-signal (one retry)
         ├─ On needs_iteration with files → proceed to inner loop
         ├─ On needs_iteration with still no files → PartResult.AgentCrashed(
         │   "Reviewer signaled needs_iteration twice without writing feedback files")
         └─ On pass → proceed to step 3 (reviewer changed its mind)
   f. ── INNER FEEDBACK LOOP ──
      │
      ├─ List files in pending/ matching critical__* (sorted by filename)
      │   For each file → PROCESS_FEEDBACK_ITEM (see below)
      │
      ├─ List files in pending/ matching important__* (sorted by filename)
      │   For each file → PROCESS_FEEDBACK_ITEM (see below)
      │
      ├─ List files in pending/ matching optional__* (sorted by filename)
      │   For each file:
      │   ├─ Same as PROCESS_FEEDBACK_ITEM but with skip guidance in instructions
      │   ├─ "This is OPTIONAL. Address if worthwhile, or write
      │   │    '## Resolution: ADDRESSED' noting you chose to skip, then signal done."
      │   └─ Skipped optional items: harness moves to addressed/ (doer explicitly chose to skip)
      │
      └─ END INNER LOOP
   g. Validate: pending/ contains no critical__* or important__* files
      ├─ If not empty → BUG — all critical/important should have been processed.
      │   PartResult.AgentCrashed("Critical/important feedback not processed after inner loop")
      └─ If empty → proceed
   h. Assemble reviewer instructions (includes addressed/ and rejected/ file contents)
   i. Deliver to reviewer (re-instruction pattern)
   j. Await reviewer done signal → go to step 3 (PASS) or step 4 (NEEDS_ITERATION)
```

### PROCESS_FEEDBACK_ITEM — Per-Item Flow

```
PROCESS_FEEDBACK_ITEM(file):
    ├─ Self-compaction check at done boundary (ref.ap.8nwz2AHf503xwq8fKuLcl.E)
    ├─ Assemble doer instructions with THIS ONE feedback item
    ├─ Deliver via AckedPayloadSender to existing doer session (re-instruction pattern)
    ├─ Await doer done signal (health-aware await loop)
    ├─ PUBLIC.md validation (shallow — exists, non-empty)
    ├─ Read ## Resolution marker from feedback file
    │   ├─ Missing marker → re-instruct doer (one retry, then AgentCrashed)
    │   ├─ ADDRESSED → harness moves file to addressed/, GitCommitStrategy.onSubPartDone
    │   └─ REJECTED → REJECTION_NEGOTIATION(file)
    └─ (next file)
```

### REJECTION_NEGOTIATION — Per-Item Disagreement Resolution (RejectionNegotiationUseCase / ap.fvpIuw4Yeeq1IXDvLC3mL.E)

```
REJECTION_NEGOTIATION(file):
    disagreementRound = 0

    loop:
        disagreementRound++

        ├─ Send rejection + doer's reasoning to reviewer (idle session, re-instruction pattern)
        │   "The implementor rejected this feedback item. Review the reasoning below
        │    and decide: accept the rejection (done pass) or insist (done needs_iteration)
        │    with counter-reasoning."
        ├─ Await reviewer done signal
        │
        ├─ if reviewer signals PASS:
        │   └─ Reviewer accepts rejection → harness moves file to rejected/
        │      GitCommitStrategy.onSubPartDone → return
        │
        └─ if reviewer signals NEEDS_ITERATION:
            ├─ if disagreementRound >= 2:
            │   └─ Max disagreement rounds reached. Doer MUST comply.
            │      Send to doer: "Reviewer has insisted twice. You MUST address this item.
            │      Write '## Resolution: ADDRESSED' with your implementation notes."
            │      Await doer done → validate ADDRESSED (AgentCrashed if still REJECTED)
            │      Harness moves to addressed/ → return
            │
            └─ if disagreementRound < 2:
                ├─ Send back to doer with reviewer's counter-reasoning:
                │   "Reviewer insists this must be addressed. Their reasoning:
                │    <reviewer's counter-reasoning from PUBLIC.md>.
                │    Address this item or provide stronger rejection reasoning."
                ├─ Await doer done
                ├─ Read ## Resolution marker
                ├─ if ADDRESSED → harness moves to addressed/ → return
                └─ if REJECTED → continue loop (next disagreement round)
```

**Properties:**
- **Bounded:** At most 2 rounds of disagreement. After round 2, doer must comply.
- **Reviewer is authority:** The reviewer's insistence is the escalation mechanism.
- **Uses existing signals:** Reviewer uses `done pass` (accept) or `done needs_iteration` (insist).
  No protocol extension needed.
- **Self-compaction friendly:** Each negotiation step is a done boundary.
- **Uses existing infrastructure:** Both sessions are already alive. The re-instruction pattern
  (`sendPayloadWithAck` to existing session) handles delivery to the idle session.

### Connection to Self-Compaction (ref.ap.8nwz2AHf503xwq8fKuLcl.E)

The inner feedback loop creates **frequent done boundaries** — one after each feedback item,
and additional boundaries during rejection negotiation. Each done boundary is a natural
soft-compaction checkpoint. The harness reads `context_window_slim.json` after every item and
compacts before feeding the next if the threshold is crossed.

This is a significant improvement over the current design where the doer processes ALL
reviewer feedback in one go. In the current design:
- If the doer runs out of context mid-re-work → emergency interrupt (Ctrl+C), forced
  compaction, session rotation, agent must resume from PRIVATE.md
- The emergency path is disruptive and risks lost work

With the inner feedback loop:
- Soft compaction triggers between items while the agent has ample room to summarize
- The agent finishes one item, compacts, starts fresh on the next
- Emergency interrupts become rare — the soft threshold (35% remaining / 65% used) catches most cases

**The inner loop makes self-compaction proactive rather than reactive.**

### Doer Instructions Per Feedback Item

The doer receives a focused instruction per item:

```markdown
## Feedback Item to Address

<contents of the feedback markdown file>

## Instructions

1. Address the feedback item above in the codebase.
2. Write a `## Resolution: ADDRESSED` section at the bottom of the feedback file at
   `<absolute path to feedback file>` describing what you did and why.
3. If you disagree with this feedback, write `## Resolution: REJECTED` instead with
   a clear justification explaining WHY the feedback should not be applied
   (ref.ap.kmiKk7vECiNSpJjAXYMyE.E).
4. Update your PUBLIC.md with a brief one-liner noting this item was addressed/rejected.
5. Signal done: `callback_shepherd.signal.sh done completed`

### Feedback file path
`<absolute path to the file in pending/>`
```

For optional items, add:
```markdown
**This feedback is OPTIONAL.** You may choose to skip it. If skipping, write
`## Resolution: ADDRESSED` noting you reviewed it and chose to skip, then signal done.
```

### Reviewer Instructions for Rejection Judgment

When the doer rejects a feedback item, the reviewer receives:

```markdown
## Feedback Item (REJECTED by implementor)

<original feedback file content including ## Resolution: REJECTED reasoning>

## Your Decision

Review the implementor's rejection reasoning above.

- If the rejection reasoning is sound, signal: `callback_shepherd.signal.sh done pass`
- If this feedback must be addressed despite the implementor's objection, explain WHY in
  your PUBLIC.md and signal: `callback_shepherd.signal.sh done needs_iteration`
```

### Reviewer Instructions — Full Pass (After Inner Loop)

On subsequent iterations (after inner loop), the reviewer's instruction file
(assembled by `ContextForAgentProvider` ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) includes:

| # | Section | Source | Notes |
|---|---------|--------|-------|
| 7a | **Addressed feedback** | `__feedback/addressed/*.md` | What the doer addressed — verify fixes are correct. Each file contains the doer's `## Resolution: ADDRESSED` with implementation notes. |
| 7b | **Rejected feedback** | `__feedback/rejected/*.md` | Items where the reviewer previously accepted the doer's rejection reasoning. Review if still appropriate given new changes. |
| 7c | **Remaining optional feedback** | `__feedback/pending/optional__*.md` | Optional items the doer chose to skip — reviewer can accept or write new critical/important items if the skip was wrong. |
| 7d | **Feedback writing instructions** | Static text | How to write new feedback files to `__feedback/pending/` with severity prefix. |

The reviewer is instructed:
- Write **new** feedback to `__feedback/pending/` (one file per issue, with severity prefix)
- Signal `pass` only if no critical or important issues remain
- Signal `needs_iteration` if there are items to process

### Part Completion Guard

The harness enforces a **hard constraint** before allowing `PartResult.Completed`:

```
On reviewer PASS:
  1. PUBLIC.md validation (existing)
  2. Validate __feedback/pending/ contains no critical__* files
  3. Validate __feedback/pending/ contains no important__* files
  4. If 2 or 3 fails → do NOT complete. Log ERROR.
     Re-instruct reviewer: "You signaled pass but there are unaddressed critical/important
     feedback items in pending/. Write new feedback files for any remaining issues or
     signal needs_iteration."
     Await reviewer's re-signal. (One retry, then PartResult.AgentCrashed.)
  6. Optional items in pending/ → acceptable. Part completes.
     Harness moves remaining optional__* to addressed/ (implicitly accepted as skipped).
```

---

## Spec Documents Requiring Updates

| Document | Change | Scope |
|----------|--------|-------|
| `doc/schema/ai-out-directory.md` (ref.ap.BXQlLDTec7cVVOrzXWfR7.E) | Add `__feedback/` directory tree (3 dirs: pending/, addressed/, rejected/) at part level. Add feedback file format with severity prefix. Update directory tree diagram. | Directory schema |
| `doc/core/PartExecutor.md` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) | Add inner feedback loop to PartExecutorImpl flow (step 4). Add PROCESS_FEEDBACK_ITEM and REJECTION_NEGOTIATION flows. Add part completion guard. Reference self-compaction synergy. | Executor logic |
| `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) | Add per-feedback-item doer instruction assembly. Add rejection judgment reviewer instruction. Update reviewer instruction concatenation table (sections 7a–7d). Update structured feedback contract (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) — reviewer now writes individual files instead of inline issues in PUBLIC.md. | Instruction assembly |
| `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E) | Add section on inner feedback loop as frequent done-boundary source. Note that granular feedback loop reduces emergency compaction frequency. Cross-reference ref.ap.5Y5s8gqykzGN1TVK5MZdS.E. | Self-compaction synergy |
| `doc/high-level.md` | Update "Sub-Part Transitions" section — reviewer-driven iteration now includes inner feedback loop with rejection negotiation. Add link to this spec. | High-level overview |
| `doc/schema/plan-and-current-state.md` (ref.ap.56azZbk7lAMll0D4Ot2G0.E) | Clarify that iteration.current semantics are unchanged — inner loop does not increment. No schema change needed. | Schema clarification |

---

## Requirements

### R1: `__feedback/` Directory Structure in `.ai_out/`
- Three directories created at part setup: `pending/`, `addressed/`, `rejected/`
- Lives at `execution/${part_name}/__feedback/`, not under sub-parts
- Created by `AiOutputStructure.ensureStructure()` (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
- Empty at creation — no placeholder files
- Verifiable: directory creation test; `ls -R` shows all three directories

### R2: Reviewer Writes Individual Feedback Files to `__feedback/pending/`
- One markdown file per actionable issue
- Filename format: `{severity}__{descriptive-slug}.md`
- Valid severity prefixes: `critical__`, `important__`, `optional__`
- Reviewer instructions updated to include file format, severity prefixes, and directory path
- Reviewer's PUBLIC.md remains the work log (verdict, what passed, overall assessment)
- Structured feedback contract (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) updated: issues go to
  individual files, not inline in PUBLIC.md
- Verifiable: reviewer instructions contain `__feedback/pending/` path and severity prefix guidance

### R3: Inner Feedback Loop in PartExecutorImpl
- After reviewer `needs_iteration`: list files in `pending/`, process in severity order
  (critical → important → optional, sorted by filename within severity)
- Feed ONE feedback file at a time to the doer via re-instruction pattern
- Doer signals `done completed` after each item
- Harness reads `## Resolution:` marker from feedback file after doer's `done`
- Missing marker → re-instruct doer (one retry, then AgentCrashed)
- `ADDRESSED` → harness moves file to `addressed/`
- `REJECTED` → triggers rejection negotiation (R5)
- Optional items: doer can skip (writes `## Resolution: ADDRESSED` noting skip)
- Self-compaction check (ref.ap.8nwz2AHf503xwq8fKuLcl.E) at each done boundary
- Git commit after each processed item
- Verifiable: unit test with FakeAgentFacade — items processed in severity order,
  resolution marker read correctly, files moved by harness

### R4: Doer Per-Item Instructions via ContextForAgentProvider
- New method or parameterization in `ContextForAgentProvider`
  (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E): assemble instructions for a single feedback item
- Includes: feedback file content, resolution marker instructions, feedback file path,
  PUBLIC.md one-liner guidance
- Optional items include skip guidance
- Verifiable: unit test — instruction content includes exactly one feedback item and
  resolution marker instructions

### R5: Per-Item Rejection Negotiation (RejectionNegotiationUseCase — ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E)
- **Encapsulated as `RejectionNegotiationUseCase`** — self-contained sub use case, called by
  `PartExecutorImpl` from within the inner feedback loop
- **Why inline:** Focus. Both agents have the specific item in context at the point of
  contention. Deferring loses context and breaks the reasoning chain (see D7).
- **Interface:** Receives `AgentFacade`, both agent handles, feedback file path.
  Returns `RejectionResult` sealed class: `Accepted`, `AddressedAfterInsistence`, `AgentCrashed`
- When doer writes `## Resolution: REJECTED`, harness sends rejection + reasoning to reviewer
- Reviewer signals `done pass` (accept rejection) or `done needs_iteration` (insist)
- If reviewer accepts → `RejectionResult.Accepted` → harness moves file to `rejected/`
- If reviewer insists → item sent back to doer with counter-reasoning
- At most **2 rounds of disagreement** per item
- After 2 rounds of reviewer insistence, doer MUST address (third rejection → `RejectionResult.AgentCrashed`)
- Reuses existing signal semantics (no protocol extension)
- **Testability:** Fully unit-testable via `FakeAgentFacade` + virtual time. Each negotiation
  scenario (accept, insist→address, insist→reject→insist→forced, crash) is an independent test.
  The use case is tested in isolation from `PartExecutorImpl` — clean boundary.
- Verifiable: unit test — full negotiation flow: reject → insist → address;
  reject → accept; reject → insist → reject → insist → forced compliance

### R6: Resolution Marker in Feedback Files
- Doer appends `## Resolution: ADDRESSED` or `## Resolution: REJECTED` to the feedback file
- `ADDRESSED` includes brief implementation notes
- `REJECTED` includes clear justification (WHY-NOT — ref.ap.kmiKk7vECiNSpJjAXYMyE.E)
- Harness reads the marker: scan for `## Resolution:` line, extract keyword
- Harness does NOT validate the reasoning content (same KISS principle as PUBLIC.md format —
  ref.ap.EslyJMFQq8BBrFXCzYw5P.E enforcement boundary)
- Verifiable: harness correctly parses ADDRESSED, REJECTED, and missing marker cases

### R7: Reviewer Instructions Include `__feedback/` State (Full Pass)
- On iteration > 1: reviewer sees `addressed/` and `rejected/` file contents
- Remaining `pending/optional__*` files visible as "skipped optional items"
- Reviewer writes NEW feedback to `pending/` for new issues (with severity prefix)
- Verifiable: reviewer instruction concatenation includes feedback directory contents

### R8: Part Completion Guard — No Pending Critical/Important
- After reviewer `PASS` + PUBLIC.md validation: harness checks `pending/` for
  `critical__*` and `important__*` files
- If found: re-instruct reviewer (one retry, then AgentCrashed)
- Remaining `optional__*` files → acceptable; harness moves to `addressed/` on completion
- Verifiable: unit test — PASS with pending critical → re-instruction → eventual
  completion or failure

### R9: Feedback Files Presence Guard on `needs_iteration`
- After reviewer signals `needs_iteration`: harness checks that at least one file exists in
  `pending/`
- If empty: re-instruct reviewer to write feedback files (one retry)
- If still empty after retry: `PartResult.AgentCrashed`
- If reviewer changes verdict to `pass` on retry: proceed to completion (step 3)
- Verifiable: unit test — needs_iteration with empty pending → re-instruction → eventual
  file creation or failure

### R10: Iteration Counter Unchanged
- `iteration.current` increments when reviewer signals `needs_iteration` — same as now
- Inner loop processing does NOT increment the counter
- Rejection negotiation rounds do NOT increment the counter
- `iteration.max` budget applies to reviewer passes, not individual feedback items
- Verifiable: unit test — multiple items + rejection negotiation in inner loop,
  counter increments once

### R11: Harness-Owned File Movement
- After doer signals `done` and resolution marker is read:
  - `ADDRESSED` → harness moves file from `pending/` to `addressed/`
  - `REJECTED` + reviewer accepts → harness moves file from `pending/` to `rejected/`
- Agents NEVER move feedback files between directories
- Harness uses standard file move operation (atomic where filesystem supports it)
- Git commit after each move captures the state change
- Verifiable: unit test — file ends up in correct directory after each scenario

---

## Implementation Gates

### Gate 1: Directory Structure + File Format
**Scope:** R1, R6
**What:** `__feedback/` directory tree in `AiOutputStructure` (3 dirs). Feedback file format
with severity prefix. Resolution marker parsing logic.
**Verify:**
- Unit test: `ensureStructure()` creates all three directories
- Unit test: resolution marker parser handles ADDRESSED, REJECTED, missing, malformed
- Spec review: file format and severity prefix documented in ai-out-directory.md
**Proceed when:** Directory structure is created reliably. Parser handles all cases.

### Gate 2: Reviewer Contract Update
**Scope:** R2, R7
**What:** Reviewer instructions updated to write individual feedback files with severity
prefix. Reviewer instructions on iteration > 1 include `addressed/` and `rejected/` contents.
Structured feedback contract (ref.ap.EslyJMFQq8BBrFXCzYw5P.E) updated.
**Verify:**
- Unit test: reviewer instructions contain `__feedback/pending/` path, severity prefix guidance
- Unit test: iteration > 1 instructions include `addressed/` and `rejected/` file contents
**Proceed when:** Reviewer can be instructed to write individual feedback files and review
prior iteration's results.

### Gate 3: Inner Feedback Loop + Doer Instructions + Guards
**Scope:** R3, R4, R9, R10, R11
**What:** Inner loop in PartExecutorImpl. Per-item doer instructions via
ContextForAgentProvider. Harness-owned file movement. Iteration counter unchanged.
**Verify:**
- Unit test: inner loop processes critical → important → optional in order
- Unit test: harness moves files to addressed/ after ADDRESSED resolution
- Unit test: missing resolution marker → re-instruction → eventual resolution or failure
- Unit test: self-compaction check fires at each done boundary
- Unit test: iteration.current increments once per reviewer needs_iteration, not per item
- Unit test: needs_iteration with empty pending → re-instruction → reviewer writes files
- Unit test: needs_iteration with empty pending after retry → AgentCrashed
**Proceed when:** Full inner loop works with FakeAgentFacade, including guards and file movement.

### Gate 4: Rejection Negotiation (RejectionNegotiationUseCase — ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E)
**Scope:** R5
**What:** `RejectionNegotiationUseCase` — self-contained sub use case extracted from
`PartExecutorImpl`. Per-item rejection negotiation flow. Bounded at 2 disagreement
rounds. Tested in isolation via `FakeAgentFacade` + virtual time.
**Verify:**
- Unit test: REJECTED → reviewer accepts (pass) → `RejectionResult.Accepted` → file moved to rejected/
- Unit test: REJECTED → reviewer insists (needs_iteration) → back to doer → doer addresses → `RejectionResult.AddressedAfterInsistence`
- Unit test: REJECTED → 2 rounds of insistence → doer forced to comply → `RejectionResult.AddressedAfterInsistence`
- Unit test: REJECTED → 2 rounds + doer still rejects → `RejectionResult.AgentCrashed`
- Unit test: reviewer uses existing session (re-instruction to idle session)
- Unit test: self-compaction check between negotiation rounds
**Proceed when:** Full negotiation flow works including all boundary cases. Use case tests
pass independently of `PartExecutorImpl` tests.

### Gate 5: Part Completion Guard
**Scope:** R8
**What:** Harness validates no critical/important in `pending/` before completing.
**Verify:**
- Unit test: PASS with empty pending (no critical/important) → Completed
- Unit test: PASS with critical__* in pending → re-instruction → eventual handling
- Unit test: PASS with only optional__* in pending → Completed (optional moved to addressed/)
**Proceed when:** Part completion is correctly guarded.

### Gate 6: Integration Validation
**Scope:** All requirements end-to-end
**What:** Integration test with real agent (or GLM fake) confirming the full feedback loop.
**Verify:**
- Reviewer writes individual feedback files with severity prefixes
- Doer receives one item at a time, addresses/rejects each with resolution marker
- Rejection negotiation works between real agents
- Harness moves files correctly after each item
- Self-compaction triggers between items when context is low
- Iteration counter behaves correctly
- Part completion guard works
**Proceed when:** Full flow validated with real or realistic agent.

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Reviewer writes monolithic PUBLIC.md instead of individual files** | Inner loop has nothing to process; iteration stalls | Harness-enforced guard (R9): if `needs_iteration` but `pending/` is empty → re-instruct reviewer. One retry, then AgentCrashed. |
| **Too many feedback files overwhelm the inner loop** | Many small items means many done→compaction cycles; overhead accumulates | Each cycle is lightweight (one file read, one instruction assembly). 20 items × 2 min each = 40 min — comparable to current single-pass behavior but with better context management. |
| **Severity classification is subjective** | Reviewer calls something "optional" that should be "critical" | Severity determines processing order and skip rules, not quality. The reviewer is the severity authority. |
| **Doer rejects everything** | All items go through rejection negotiation; expensive | Bounded at 2 rounds per item. `iteration.max` budget still applies at the macro level. Persistent rejection after forced compliance → AgentCrashed. |
| **Resolution marker missing or malformed** | Harness can't determine disposition | Re-instruct doer with explicit marker format (one retry, then AgentCrashed). Marker format is deliberately simple (`## Resolution: ADDRESSED/REJECTED`) to minimize parse failures. |
| **Reviewer always insists on rejected items** | 2 rounds × N items → expensive negotiation | This is correct behavior — the reviewer IS the authority. The cost is proportional to the number of genuine disagreements, which should be small in practice. |

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
   `needs_iteration` but `pending/` is empty → re-instruct reviewer to write feedback files.
   One retry, then `AgentCrashed`. See R9.

5. **Who moves files?** Harness owns all file movement. Agents write resolution markers in
   feedback files; harness reads markers and moves files. This eliminates agent file-operation
   failures as a failure class entirely.

6. **How are rejections resolved?** Per-item rejection negotiation. Doer rejects → reviewer
   judges → accept or insist. Bounded at 2 disagreement rounds. Reviewer is authority.
   Resolves disagreements at the point of contention instead of deferring to full iteration cycle.

---

## Design Change Summary (vs. Previous Version)

| Aspect | Previous Design | Current Design |
|--------|----------------|----------------|
| **Directories** | 9 (3 statuses × 3 severities) | 3 (`pending/`, `addressed/`, `rejected/`) |
| **Severity encoding** | Directory structure | Filename prefix (`critical__`, `important__`, `optional__`) |
| **File movement** | Agents move files; harness validates | **Harness** moves files; agents write resolution markers |
| **Movement records** | Agents append movement log entries | Eliminated — resolution marker in file is sufficient |
| **Rejection handling** | Reviewer moves rejected items back to unaddressed on next full pass | Per-item negotiation: doer rejects → reviewer judges → bounded resolution |
| **File-move failure paths** | 4 distinct validation/retry/crash paths | Zero — harness is the mover |
| **Disagreement resolution** | Deferred to next full iteration cycle | Immediate, bounded at 2 rounds per item |
| **Agent cognitive load** | Must understand 9-directory structure + movement mechanics | Write resolution marker in the file they're already editing |
