# Exploration Summary

## Current State
- 7 shared InstructionSection subtypes already implemented: RoleDefinition, PrivateMd, PartContext, Ticket, OutputPathSection, WritingGuidelines, CallbackHelp
- InstructionRenderers already has: roleCatalog(), callbackScriptUsage(), feedbackItemInstructions(), partContext(), publicMdOutputPath()
- InstructionText already has: DOER_PUSHBACK_GUIDANCE, AGENT_TYPES_AND_MODELS, PLAN_FORMAT_INSTRUCTIONS
- Full request hierarchy in place: DoerRequest, ReviewerRequest, PlannerRequest, PlanReviewerRequest
- Test fixtures in ContextTestFixtures with factory methods for all 4 request types

## Key Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` — add 7 new subtypes
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionRenderers.kt` — rendering functions
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionText.kt` — static text constants
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` — request types
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` — existing tests
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextTestFixtures.kt` — test fixtures

## 7 Subtypes to Add
1. PlanMd — reads plan from ExecutionContext.planMdPath, null=skip
2. PriorPublicMd — renders list from ExecutionContext.priorPublicMdPaths
3. IterationFeedback — reviewer feedback + pushback guidance, null reviewerPublicMdPath=skip
4. InlineFileContentSection(heading, path?) — generic file reader, null=skip, non-null+missing=FAIL
5. RoleCatalog — delegates to InstructionRenderers.roleCatalog()
6. AvailableAgentTypes — static InstructionText.AGENT_TYPES_AND_MODELS
7. PlanFormatInstructions — static InstructionText.PLAN_FORMAT_INSTRUCTIONS
