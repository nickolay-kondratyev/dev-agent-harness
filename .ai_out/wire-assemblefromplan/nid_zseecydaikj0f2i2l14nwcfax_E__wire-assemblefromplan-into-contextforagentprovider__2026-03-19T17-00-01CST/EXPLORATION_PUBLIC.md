# Exploration Summary

## Current State
- `ContextForAgentProviderImpl` uses procedural `build*Sections()` pattern (4 methods + ~15 helper methods)
- `InstructionSection` sealed class with 18 subtypes — fully implemented and tested
- `InstructionPlanAssembler` — rendering engine fully implemented and tested
- `InstructionRenderers` — rendering functions still used by some InstructionSection subtypes (delegation)
- All existing tests pass through `ContextForAgentProvider.standard(outFactory)`

## Key Design Insight
Per-role plans CANNOT be static `val` lists because parameterized sections (`InlineFileContentSection`, `OutputPathSection`, `FeedbackDirectorySection`, `CallbackHelp`) need concrete values from the request. Plans must be constructed per-call inside the `when` dispatch.

## Migration Plan
1. Replace `assembleInstructions` to dispatch via sealed `when`, build plan per request, delegate to `InstructionPlanAssembler.assembleFromPlan`
2. Delete all `build*Sections()` and helper methods
3. Delete `InstructionRenderers.kt` (already absorbed by InstructionSection subtypes)
4. Inline rendering from `InstructionRenderers` functions used by InstructionSection subtypes
5. Update existing tests + add section ordering tests per AC
