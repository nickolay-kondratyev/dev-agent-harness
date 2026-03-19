# TOP_LEVEL_AGENT: InstructionSection + assembleFromPlan

## Workflow Phases
1. ✅ EXPLORATION — Explored existing ContextForAgentProviderImpl, InstructionRenderers, InstructionText, AgentInstructionRequest, spec
2. ✅ CLARIFICATION — No ambiguities, requirements clear from ticket + spec
3. ✅ IMPLEMENTATION_WITH_SELF_PLAN — Created InstructionSection sealed class (7 subtypes) + InstructionPlanAssembler
4. ✅ IMPLEMENTATION_REVIEW — 2 issues found (OutputPathSection template divergence, test assertion lie)
5. ✅ IMPLEMENTATION_ITERATION — Both issues addressed. Tests pass.

## Final State
- All tests pass (`./gradlew :app:test`)
- 4 new files created
- No existing files modified
