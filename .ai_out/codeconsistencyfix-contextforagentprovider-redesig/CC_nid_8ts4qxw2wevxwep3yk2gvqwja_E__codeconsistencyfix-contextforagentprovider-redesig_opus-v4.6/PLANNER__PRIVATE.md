# Planner Private Context

## Key Findings from Exploration

- **Zero external callers** -- `assembleInstructions`, `UnifiedInstructionRequest`, and `AgentRole` are only used within `core/context` package (2 production files, 4 test files + 1 fixture file).
- `RoleDefinition` is from `com.glassthought.shepherd.core.agent.rolecatalog` -- simple data class with `name`, `description`, `descriptionLong?`, `filePath`.
- No existing PrivateMd handling anywhere in the codebase (grep confirmed).

## Decisions Made

### feedbackDir non-nullable on ReviewerRequest
The current code does `if (request.iterationNumber > 1 && request.feedbackDir != null)` in `buildReviewerSections`. Making `feedbackDir` non-nullable means the iteration-1 fixture must create an empty feedback directory. The existing `collectFeedbackFiles` and `collectMarkdownFilesInDir` methods already handle empty/non-existent subdirectories gracefully (they return `emptyList()`). So the conditional simplifies to `if (request.iterationNumber > 1)`.

### PrivateMd path derivation
`outputDir` = `${sub_part}/comm/in` per the `.ai_out/` directory schema.
- `outputDir.parent` = `${sub_part}/comm`
- `outputDir.parent.parent` = `${sub_part}`
- Target: `${sub_part}/private/PRIVATE.md`
- Formula: `outputDir.parent.parent.resolve("private/PRIVATE.md")`

### Guard tests deletion
4 guard tests that use `shouldThrow<IllegalArgumentException>` become unrepresentable with the sealed hierarchy. They must be deleted -- they served their purpose (catching runtime mistakes) and are now replaced by compile-time guarantees.

## Files Summary

| File | Action |
|------|--------|
| `app/src/main/kotlin/.../ContextForAgentProvider.kt` | Rewrite: sealed hierarchy replaces enum+flat class |
| `app/src/main/kotlin/.../ContextForAgentProviderImpl.kt` | Update: dispatch on sealed, remove requireNotNull, add privateMd |
| `app/src/test/kotlin/.../ContextTestFixtures.kt` | Update: return sealed subtypes |
| `app/src/test/kotlin/.../ContextForAgentProviderAssemblyTest.kt` | Update: remove AgentRole, fix .copy(), delete guard tests, add PrivateMd tests |
| `app/src/test/kotlin/.../ExecutionAgentInstructionsKeywordTest.kt` | Update: remove AgentRole, fix !! operators |
| `app/src/test/kotlin/.../PlannerInstructionsKeywordTest.kt` | Update: remove AgentRole, fix !! operators |
| `app/src/test/kotlin/.../PlanReviewerInstructionsKeywordTest.kt` | Update: remove AgentRole, fix !! operators |
