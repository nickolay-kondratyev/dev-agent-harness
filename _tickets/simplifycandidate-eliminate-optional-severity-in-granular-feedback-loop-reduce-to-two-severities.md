---
closed_iso: 2026-03-17T23:35:05Z
id: nid_ldztnvmnyg6lt6wjud2eeps8i_E
title: "SIMPLIFY_CANDIDATE: Eliminate optional severity in granular feedback loop — reduce to two severities"
status: closed
deps: []
links: []
created_iso: 2026-03-17T23:13:16Z
status_updated_iso: 2026-03-17T23:35:05Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE]
---

FEEDBACK:
--------------------------------------------------------------------------------
The granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E in doc/plan/granular-feedback-loop.md) has three severity levels: critical, important, optional. The optional severity creates special handling throughout:

1. **Inner loop**: optional items get different instructions with skip guidance ("This is OPTIONAL. Address if worthwhile, or write ADDRESSED noting you chose to skip")
2. **Part completion guard** (line 441-448): remaining optional__* files are acceptable and moved to addressed/ on completion — special-case logic
3. **Reviewer instructions** (section 7c in doc/core/ContextForAgentProvider.md): remaining optional feedback shown separately
4. **Blocking rules** (D4 in the spec): optional items have different blocking semantics than critical/important

**Proposed simplification:** Eliminate the `optional__` severity prefix entirely. Use only `critical__` and `important__`.

**Why this works just as well:**
- If feedback is truly optional, the reviewer should note it in PUBLIC.md (the work log) rather than creating a dedicated feedback file that enters the per-item processing loop
- Processing optional items one at a time through the inner loop is overhead for items that may be skipped anyway
- The reviewer already controls what to file — choosing not to file optional items is simpler than filing them with special skip semantics

**Robustness improvement:**
- Fewer code paths = fewer edge cases. The inner loop, completion guard, instruction assembly, and reviewer instructions all become simpler
- Eliminates the ambiguous "should I skip this?" decision for the doer
- Two clear severities: must-address (critical) and should-address (important). Binary decision, no gray area

Affected specs:
- doc/plan/granular-feedback-loop.md (D4, inner loop, completion guard, reviewer instructions)
- doc/core/ContextForAgentProvider.md (sections 6c-6e, InstructionSection sealed class)


--------------------------------------------------------------------------------
DECISION: KEEP optional, adjust justification writing to be
"This is OPTIONAL. Address if worthwhile, or write SKIPPED noting you chose to skip"
We want to have a chance to address optional feedback otherwise smaller improvements will either be left behind completely, or require human to deep dive into it. Lets 1) adjust to add SKIPPED state not just ADDRESSED for optional feedback. 2) document the importance of optional feedback in the flow.
## Notes

**2026-03-17T23:35:21Z**

## Resolution

Decision: KEEP optional severity. Two spec changes made:

### 1. Added SKIPPED resolution marker
- New `## Resolution: SKIPPED` marker for optional items (alongside ADDRESSED and REJECTED)
- Valid only for `optional__` prefixed files
- Harness moves SKIPPED files to `addressed/` (same destination, different semantics)
- Doer intent is now explicit: "I saw this and chose not to act" vs "I addressed this"

### 2. Documented importance of optional feedback
- Added "Why optional feedback is kept" paragraph in D4 (blocking rules) of granular-feedback-loop.md
- Explains the middle ground between `important__` (forced) and PUBLIC.md-only (lost)

### Files changed
- `doc/plan/granular-feedback-loop.md` — Resolution marker format, inner loop guidance, D4 documentation, R3, R6
- `doc/core/ContextForAgentProvider.md` — Structured feedback contract, FeedbackItem section type, fixed "at most 2" to "at most 1" disagreement round
