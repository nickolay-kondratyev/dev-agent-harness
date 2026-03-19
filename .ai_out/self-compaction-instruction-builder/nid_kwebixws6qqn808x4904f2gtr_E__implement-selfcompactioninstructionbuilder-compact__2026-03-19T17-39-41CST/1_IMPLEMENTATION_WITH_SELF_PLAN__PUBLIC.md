# SelfCompactionInstructionBuilder Implementation

## What was done

Implemented `SelfCompactionInstructionBuilder` (R8 of ContextWindowSelfCompactionUseCase) with full test coverage.

### Changes

1. **Added `SELF_COMPACTED` signal constant** to `ProtocolVocabulary.Signal` in `app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt`

2. **Created `SelfCompactionInstructionBuilder`** at `app/src/main/kotlin/com/glassthought/shepherd/core/compaction/SelfCompactionInstructionBuilder.kt`
   - Class with `build(privateMdAbsolutePath: Path): String` method
   - Uses `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` and `ProtocolVocabulary.Signal.SELF_COMPACTED`
   - Renders the self-compaction instruction template with the provided PRIVATE.md path

3. **Created unit tests** at `app/src/test/kotlin/com/glassthought/shepherd/core/compaction/SelfCompactionInstructionBuilderTest.kt`
   - 10 test cases covering: path rendering, callback command, all summarization guidelines, conciseness instruction
   - BDD style with GIVEN/WHEN/THEN, one assert per `it` block

### Test results
- All tests pass (`BUILD SUCCESSFUL`)
- New test class `SelfCompactionInstructionBuilderTest` verified in test results

### Design decisions
- Used `class` (not `object`) per spec requirement, even though it is stateless — this allows future constructor injection if needed
- Used `trimMargin()` for clean multi-line template rendering
- Referenced `ProtocolVocabulary` constants for compile-time safety on protocol keywords
