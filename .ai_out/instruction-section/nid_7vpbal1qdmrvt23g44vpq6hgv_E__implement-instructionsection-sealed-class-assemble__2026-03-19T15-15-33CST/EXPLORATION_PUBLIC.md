# Exploration: InstructionSection + assembleFromPlan

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` — interface + AgentInstructionRequest sealed class
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` — current impl with buildList pattern
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionRenderers.kt` — reusable renderers (partContext, publicMdOutputPath, callbackScriptUsage, roleCatalog, feedbackItemInstructions)
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionText.kt` — static constants
- `doc/core/ContextForAgentProvider.md` — spec with "Internal Design: Data-Driven Assembly" section

## Scope (this ticket): 7 shared subtypes
1. RoleDefinition — renders role file content
2. PrivateMd — prior session context, silently skips if absent
3. PartContext — part name + description (execution roles only)
4. Ticket — ticket markdown content
5. OutputPathSection(label, path) — generic labeled output path
6. WritingGuidelines — static PUBLIC.md guidance
7. CallbackHelp(forReviewer, includePlanValidation) — callback script usage

## Out of scope
- InlineFileContentSection → nid_r2rdkc0t9jd9597sumbgzp7aw_E
- FeedbackDirectorySection → nid_gp9rduvxoqf14m95z9bttnaxq_E

## Existing patterns to reuse
- `InstructionRenderers.partContext()` → PartContext renderer
- `InstructionRenderers.publicMdOutputPath()` → becomes OutputPathSection
- `InstructionRenderers.callbackScriptUsage()` → CallbackHelp renderer
- `InstructionText.PUBLIC_MD_WRITING_GUIDELINES` → WritingGuidelines
- Existing private methods in ContextForAgentProviderImpl: roleDefinitionSection(), privateMdSection(), ticketSection()

## AgentInstructionRequest structure
- Common: roleDefinition, ticketContent, iterationNumber, outputDir, publicMdOutputPath, privateMdPath
- DoerRequest: executionContext, reviewerPublicMdPath
- ReviewerRequest: executionContext, doerPublicMdPath, feedbackDir
- PlannerRequest: roleCatalogEntries, planReviewerPublicMdPath, planJsonOutputPath, planMdOutputPath
- PlanReviewerRequest: planJsonContent, planMdContent, plannerPublicMdPath, priorPlanReviewerPublicMdPath

## Section separator
`"\n\n---\n\n"`

## Engine pattern (from spec)
```kotlin
private suspend fun assembleFromPlan(
    plan: List<InstructionSection>,
    request: AgentInstructionRequest,
): Path
```
