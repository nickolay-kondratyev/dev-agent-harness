# Implementation: Add explicit privateMdPath to AgentInstructionRequest

## What Was Done

Added an explicit `privateMdPath: Path?` parameter to `AgentInstructionRequest` sealed class and all 4 subtypes,
replacing the old approach that derived the PRIVATE.md path from `outputDir` directory conventions.

### Changes

1. **`ContextForAgentProvider.kt`** — Added `abstract val privateMdPath: Path?` to sealed class, with
   `override val privateMdPath: Path? = null` default in all 4 subtypes (DoerRequest, ReviewerRequest,
   PlannerRequest, PlanReviewerRequest). Default `null` ensures backward compatibility.

2. **`ContextForAgentProviderImpl.kt`** — Changed `privateMdSection(outputDir: Path)` to
   `privateMdSection(privateMdPath: Path?)`. Uses a functional chain (`takeIf`/`let`) to:
   - Skip if path is null
   - Skip if file doesn't exist
   - Skip if file is blank/empty
   - Include content with header if non-empty
   Updated all 4 `build*Sections()` call sites from `request.outputDir` to `request.privateMdPath`.

3. **`ContextForAgentProviderAssemblyTest.kt`** — Rewrote existing PRIVATE.md tests and added new ones:
   - Existing file with content -> included (doer)
   - Position verification: after role definition, before part context (doer)
   - `privateMdPath = null` -> skipped (doer)
   - Non-existent file path -> skipped, no error (doer)
   - Empty file -> skipped (doer)
   - Existing file for planner -> included with position verification

### No Changes Needed
- **`ContextTestFixtures.kt`** — No changes needed because the `privateMdPath` field has a default value of `null`,
  so all existing fixture factory methods remain compatible.

## Design Decisions

- Used `= null` default on the data class field rather than making callers explicitly pass `null`. This keeps
  all existing call sites working without modification.
- Refactored `privateMdSection` to a single-expression functional chain to satisfy detekt's `ReturnCount` rule.
- Used `isNotBlank()` (not `isNotEmpty()`) to also skip whitespace-only files.
