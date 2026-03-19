# Implementation Review: SelfCompactionInstructionBuilder

## Summary

Clean, minimal implementation of R8 (Self-Compaction Instruction Message). Three files changed:

1. **`SelfCompactionInstructionBuilder`** -- new class with `build(privateMdAbsolutePath: Path): String`
2. **`ProtocolVocabulary.Signal.SELF_COMPACTED`** -- new constant added to the existing Signal object
3. **`SelfCompactionInstructionBuilderTest`** -- 10 BDD test cases covering path rendering, callback command, and all guideline bullets

All tests pass. Sanity check passes. Template matches the spec at `ap.kY4yu9B3HGvN66RoDi0Fb.E` (lines 268-283 of `doc/use-case/ContextWindowSelfCompactionUseCase.md`).

**Overall assessment: APPROVE.** The implementation is correct, simple, and well-tested. No critical or important issues found.

## CRITICAL Issues

None.

## IMPORTANT Issues

None.

## Suggestions

1. **Minor: `trimMargin()` produces a leading newline.** The `"""...""".trimMargin()` pattern with the opening `"""` on the `return` line means the rendered string starts with `\n`. This is cosmetically harmless since the instruction will be embedded in a larger payload, but worth being aware of. If the caller trims whitespace or if the payload protocol is whitespace-insensitive, this is a non-issue. If it matters, the fix is straightforward:

   ```kotlin
   return """
       |Your context window is running low...
   """.trimMargin()
   ```
   becomes:
   ```kotlin
   return """|Your context window is running low...
       |...
   """.trimMargin()
   ```

   **Verdict:** Not blocking -- verify with the caller when it is implemented (R2 `performCompaction`).

2. **Test completeness: no test for the overall structure/ordering.** The tests verify individual fragments via `shouldContain`, which is the right approach for resilience against minor template tweaks. However, there is no test asserting that the path appears inside backticks (`` ` ``), or that the callback command appears inside backticks. If the backticks were accidentally removed, no test would catch it. Consider adding:

   ```kotlin
   it("THEN wraps the PRIVATE.md path in backticks") {
       result shouldContain "`$privateMdPath`"
   }

   it("THEN wraps the callback command in backticks") {
       result shouldContain "`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.SELF_COMPACTED}`"
   }
   ```

   The backticks are meaningful -- they tell the agent to treat these as literal commands/paths. Losing them could degrade agent behavior.

   **Verdict:** Low risk but easy to add. Recommended.

## Documentation Updates Needed

None. The implementation is self-contained and the spec already documents R8 thoroughly.
