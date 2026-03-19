# Review Notes (Private)

## Key findings

1. **OutputPathSection format mismatch**: The unified `OutputPathSection` template diverges from 3 existing implementations. This is the most important finding because when the wiring ticket plugs these sections in, agents will receive differently formatted instructions. The PUBLIC.md heading changes from `## Output Path` to `## PUBLIC.md Output Path` and the PLAN.md body changes from "human-readable plan" to "PLAN.md". Flagged as IMPORTANT, not CRITICAL, because the wiring ticket hasn't landed yet and this could be an intentional simplification.

2. **Test lie at line 268-270**: The test named "THEN does NOT include validate-plan query" actually asserts `result shouldContain "Communicating with the Harness"` which is a positive check. It doesn't verify absence of validate-plan at all. This is exactly the kind of misleading test that CLAUDE.md warns against.

3. **No security concerns**: No secrets, no injection vectors, no resource leaks. File I/O is properly dispatched on IO dispatcher.

4. **No existing functionality removed**: ContextForAgentProviderImpl is completely untouched. No tests removed.

## What I verified line by line

- InstructionSection.kt: Each render() matches the corresponding private method in ContextForAgentProviderImpl EXCEPT OutputPathSection (see finding #1).
- InstructionPlanAssembler.kt: SECTION_SEPARATOR matches ContextForAgentProviderImpl's SECTION_SEPARATOR. File writing logic matches writeInstructionsFile(). Logging matches.
- Test fixtures: ContextTestFixtures methods produce valid request objects. privateMdPath defaults to null in DoerRequest.
- Test completeness: All 7 subtypes tested. PrivateMd has 4 edge case tests. PartContext tested for all 4 request types. CallbackHelp tested for both reviewer and non-reviewer.
