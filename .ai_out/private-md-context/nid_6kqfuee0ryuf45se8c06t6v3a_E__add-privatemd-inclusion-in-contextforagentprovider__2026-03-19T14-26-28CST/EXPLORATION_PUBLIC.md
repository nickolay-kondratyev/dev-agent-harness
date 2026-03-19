# Exploration: PRIVATE.md Inclusion in ContextForAgentProvider

## Current State

PRIVATE.md support **partially exists**. The current implementation:
- Derives path from `outputDir.parent.parent.resolve("private/PRIVATE.md")` (directory convention)
- Includes content with `# Prior Session Context (PRIVATE.md)` header
- Silently skips when file doesn't exist
- Applied in all 4 `build*Sections()` methods at position 2 (after role definition)

## What Needs to Change (per ticket)

1. **Add `privateMdPath: Path?`** to each `AgentInstructionRequest` subtype (DoerRequest, ReviewerRequest, PlannerRequest, PlanReviewerRequest)
2. **Change `privateMdSection`** to accept `Path?` instead of deriving from `outputDir`
3. **Add empty-file check** — if file exists but is empty, skip silently
4. **Update test fixtures** to include new field (default to `null` for backward compat)
5. **Add missing tests**: non-existent file path, empty file, all 4 request types, position verification

## Key Files

| File | Role |
|------|------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` | Request type definitions |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` | Assembly implementation |
| `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt` | Structural tests |
| `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt` | Test fixtures |

## Section Ordering (unchanged)

Position 2 across all 4 roles: Role definition → **PRIVATE.md** → Part context/Ticket → ...

## Design Decision

The ticket wants an **explicit path parameter** rather than deriving from directory layout because:
- Caller (PartExecutor) decides when PRIVATE.md should be loaded (only after self-compaction)
- Decouples instruction assembly from directory layout knowledge
- Makes the contract explicit
