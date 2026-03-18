# Review: ContextForAgentProvider Redesign (nid_8ts4qxw2wevxwep3yk2gvqwja_E)

## VERDICT: READY (with one documentation fix needed)

All tests pass (`./sanity_check.sh` and `./test.sh` both exit 0). No logical bugs found in the
implementation. One spec doc inconsistency requires a follow-up fix.

---

## Summary

Replaced the flat `UnifiedInstructionRequest + AgentRole` enum with a sealed
`AgentInstructionRequest` hierarchy (`DoerRequest`, `ReviewerRequest`, `PlannerRequest`,
`PlanReviewerRequest`). Shared fields for doer/reviewer are composed into `ExecutionContext`.

Key effects:
- `assembleInstructions(role, request)` becomes `assembleInstructions(request)` — role encoded in type
- All `requireNotNull` runtime guards eliminated — compile-time via typed subtypes
- `PrivateMd` section added to all 4 role plans (at position 2, after RoleDefinition)
- 4 runtime-guard tests removed; 3 PrivateMd tests added
- `ReviewerRequest.feedbackDir` is now always non-null (was nullable in old API)

---

## IMPORTANT Issues

### 1. Spec doc's numbered concatenation tables do not list PrivateMd

**File:** `doc/core/ContextForAgentProvider.md` — lines 133-196 (Doer table) and 150-165 (Reviewer table), 169-178 (Planner table), 183-196 (Plan Reviewer table)

The numbered per-role tables (which the impl itself declares authoritative: "See
ContextForAgentProvider.md for the authoritative concatenation tables") do NOT include the
PrivateMd section. For example, the Doer table jumps from `1. Role definition` to
`2. Part context` with no PrivateMd row.

However, the `InstructionPlan` pseudo-code section (lines 233-254) and the implementation both
correctly include PrivateMd at position 2.

This means the doc has two conflicting statements of the section order. A reader of the numbered
tables will not know PrivateMd is inserted there.

**Fix:** Update all four numbered tables to insert a PrivateMd row at position 2. Example for Doer:
```
| 1  | Role definition  | ... |
| 2  | PrivateMd        | `${sub_part}/private/PRIVATE.md` — only after session rotation; silently skipped if absent |
| 3  | Part context     | ... |
```
Similarly for Reviewer (same positions), Planner (between row 1 and Ticket), and PlanReviewer.

**Why it matters:** The numbered tables are the human-readable spec reference for the concatenation
order. When someone adds a new role or modifies a section plan, they will read the table — and
miss the PrivateMd insertion point. The `InstructionPlan` pseudo-code lower in the doc is harder
to find and less prominent.

---

## Suggestions (non-blocking)

### A. `executionContextOrNull` extension property — used only for debug logging

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`, lines 335-341

The `executionContextOrNull` private extension property exists solely to extract `partName`
for debug log output. It adds a `when` expression to support that one logging line. This is
not wrong, but the cost/benefit is modest: the only consumer is `out.debug(...)` which is
already lazy-evaluated and only runs when DEBUG logging is enabled. If this were inlined into
the debug lambda (extracting `partName` via a when on `request`), the extension property could
be removed and the indirection eliminated. Whether to do this is a judgment call — keeping it
as-is is defensible given the exhaustive `when`.

### B. `ReviewerRequest.feedbackDir` semantics on iteration 1

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt`, lines 77-86

`feedbackDir` is now non-nullable on `ReviewerRequest`. On iteration 1 there is no feedback
directory yet, but callers must still provide a `Path`. The implementation correctly guards usage
with `if (request.iterationNumber > 1)`, so there is no functional bug. But callers are forced
to either create an empty directory or pass a non-existent path. The test fixture creates the
directory (`Files.createDirectories(feedbackDir)`).

This is an acceptable trade-off (non-null is simpler than nullable-but-ignored), but it's worth
documenting in the field comment:
```kotlin
val feedbackDir: Path,   // always required; non-null even on iteration 1 (not read until iteration > 1)
```
This prevents future maintainers from wondering why a non-null path is required when iteration 1
has no feedback.

---

## What Was Verified

- `./sanity_check.sh` passes (exit 0)
- `./test.sh` passes (exit 0), all tests UP-TO-DATE with clean build
- Old `AgentRole` / `UnifiedInstructionRequest` references exist only in documentation/tickets/change_logs — no production code references remain
- `privateMdSection` path traversal: `outputDir.parent.parent` correctly resolves to `${sub_part}` for all 4 role directory structures (verified against ai-out directory schema in `doc/schema/ai-out-directory.md`)
- `feedbackStateSections` correctly guarded by `iterationNumber > 1` — the non-null `feedbackDir` on iteration 1 is never actually read
- Exhaustive `when` on sealed type in both `assembleInstructions` and `executionContextOrNull` — no else branch, compiler enforces exhaustiveness
- 4 removed runtime-guard tests are correctly replaced: the old tests verified `requireNotNull` threw; those null states are now impossible to construct (compile-time safety), so the tests are correctly removed, not silently disabled
