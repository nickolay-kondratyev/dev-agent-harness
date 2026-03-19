# Exploration: SelfCompactionInstructionBuilder

## Key Findings

### ProtocolVocabulary (`app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt`)
- `CALLBACK_SIGNAL_SCRIPT = "callback_shepherd.signal.sh"` — already exists
- `Signal` object exists with DONE, FAIL_WORKFLOW, ACK_PAYLOAD, etc.
- **Missing**: `SELF_COMPACTED` constant in `Signal` — needs to be added

### AiOutputStructure (`app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`)
- Already has `planningPrivateMd(subPartName)` and `executionPrivateMd(partName, subPartName)` methods
- Returns absolute `Path` objects
- The builder receives `privateMdAbsolutePath: Path` — caller resolves the path via AiOutputStructure

### Builder Pattern (`CommitMessageBuilder`)
- Uses `object` (stateless utility)
- Pure function, no I/O
- Constants as `private const val`

### Compaction Package
- `app/src/main/kotlin/com/glassthought/shepherd/core/compaction/` does NOT exist yet — needs creation

### Test Pattern
- BDD with `AsgardDescribeSpec`, GIVEN/WHEN/THEN
- One assert per `it` block
- `shouldBe`, `shouldContain` matchers

## Template (from spec ap.kY4yu9B3HGvN66RoDi0Fb.E)
Uses `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` + `ProtocolVocabulary.Signal.SELF_COMPACTED` to render:
```
callback_shepherd.signal.sh self-compacted
```
