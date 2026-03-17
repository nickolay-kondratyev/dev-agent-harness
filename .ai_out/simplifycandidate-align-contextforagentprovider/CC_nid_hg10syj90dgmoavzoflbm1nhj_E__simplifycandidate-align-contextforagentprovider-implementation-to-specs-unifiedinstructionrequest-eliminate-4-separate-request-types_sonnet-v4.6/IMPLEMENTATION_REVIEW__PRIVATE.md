# IMPLEMENTATION REVIEW — PRIVATE

## Test Status

- `./test.sh`: BUILD SUCCESSFUL, all tests pass.
- `./sanity_check.sh`: PASS.

## Detailed Analysis

### Spec Alignment: UnifiedInstructionRequest

The spec (`doc/core/ContextForAgentProvider.md`) defines:
```kotlin
val roleCatalogEntries: List<RoleCatalogEntry> = emptyList(),
```
(unqualified `RoleCatalogEntry`)

The implementation has:
```kotlin
val roleCatalogEntries: List<InstructionSections.RoleCatalogEntry> = emptyList(),
```

This is a spec deviation. `RoleCatalogEntry` is a rendering-layer type (`InstructionSections` is the static text renderer), and it is being leaked into the data model `UnifiedInstructionRequest`. The spec clearly intends `RoleCatalogEntry` to be standalone.

The pre-existing `PlannerInstructionRequest` also used `List<InstructionSections.RoleCatalogEntry>`, so this is a pre-existing coupling that was carried forward. However, this is the ideal moment to fix it (while the data class is being refactored to match the spec). The fix is simple: move `RoleCatalogEntry` out of `InstructionSections` to a top-level type in the `context` package.

### Interface Correctness

- Single `assembleInstructions(role: AgentRole, request: UnifiedInstructionRequest): Path` method. Matches spec exactly.
- `AgentRole` enum with `DOER, REVIEWER, PLANNER, PLAN_REVIEWER`. Matches spec exactly.
- `when(role)` is exhaustive — no `else` branch. Compiler-enforced. Correct.

### Implementation Quality

**`requireNotNull` guards:**
- DOER: `partName`, `partDescription` — correct.
- REVIEWER: `partName`, `partDescription` — correct.
- PLANNER: `planJsonOutputPath`, `planMdOutputPath` — correct.
- PLAN_REVIEWER: `planJsonContent`, `planMdContent`, `plannerPublicMdPath` — correct.

**Debug log regression:**
The old code logged `partName` for DOER and REVIEWER roles in the per-method debug call. The new unified debug at the top of `assembleInstructions` only logs `role.name` and `iterationNumber`. The `partName` is now absent from the log output for execution agents. This is a minor diagnostic regression.

**Private method naming:**
`reviewerFeedbackForDoerSection` is reused in `buildPlannerSections` to render the plan reviewer's feedback to the planner. The method name implies a doer/reviewer relationship only. The content rendered is semantically correct (it reads any passed path and wraps in "# Reviewer Feedback"), but the name is misleading in the planner context.

**Section numbering comments:**
`buildDoerSections` has a gap: steps 1-5 are numbered sequentially, then jumps to `// 7.` (FeedbackItem/section 6 is absent — that's for the inner feedback loop, handled separately per spec). This is cosmetically confusing but was pre-existing in concept.

### Test Quality

**Test coverage for `requireNotNull`:** There are no tests verifying that passing `AgentRole.DOER` with a null `partName` throws an `IllegalArgumentException`. These paths are not tested. For a pure structural refactor this is acceptable, but the `requireNotNull` guards are new behavior that merits test coverage.

**`!!` in test code:** The change correctly applies `!!` to fields that the test fixture just set to a non-null value (e.g., `request.partName!!`, `request.planJsonOutputPath!!`). This is appropriate and noted in the implementation doc.

### Behavior Preservation

All 4 test files are updated and pass. The section assembly order is verified to be identical between old and new behavior by tracing the diff and comparing old private method bodies to new ones. This is a pure structural refactor.

### Pre-existing Issues (not introduced by this PR)

1. `RoleCatalogEntry` was already nested in `InstructionSections` in the old `PlannerInstructionRequest`.
2. `reviewerFeedbackForDoerSection` reuse for planner feedback was already present.
