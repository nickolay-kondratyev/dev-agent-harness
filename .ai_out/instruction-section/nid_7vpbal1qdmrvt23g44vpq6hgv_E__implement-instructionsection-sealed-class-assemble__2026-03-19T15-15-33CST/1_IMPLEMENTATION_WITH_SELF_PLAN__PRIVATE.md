# Private Context: InstructionSection Implementation

## Status: COMPLETE

## Files Created
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionPlanAssembler.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionPlanAssemblerTest.kt`

## Key Implementation Notes

### InstructionSection sealed class
- Anchor point: ap.YkR8mNv3pLwQ2xJtF5dZs.E
- 7 subtypes implemented (this ticket scope)
- Out-of-scope subtypes tracked in separate tickets:
  - InlineFileContentSection -> nid_r2rdkc0t9jd9597sumbgzp7aw_E
  - FeedbackDirectorySection -> nid_gp9rduvxoqf14m95z9bttnaxq_E

### InstructionPlanAssembler
- Anchor point: ap.Xk7mPvR3nLwQ9tJsF2dYh.E
- Uses same SECTION_SEPARATOR constant as ContextForAgentProviderImpl
- File I/O dispatched on DispatcherProvider.io()

### Wiring (NOT done in this ticket)
- The wiring ticket (nid_zseecydaikj0f2i2l14nwcfax_E) will:
  - Replace buildList approach in ContextForAgentProviderImpl with per-role plan lists
  - Wire assembleFromPlan into assembleInstructions
  - Potentially replace ContextForAgentProviderImpl internals entirely

### Test coverage
- Each section subtype tested in isolation
- Assembler tested for: ordering, null-skipping, empty plan, path correctness
- All existing tests still pass
