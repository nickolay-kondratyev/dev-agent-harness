# Implementation: performCompaction() + Done-Boundary Trigger Detection

## What was done

Implemented self-compaction at done boundaries in `PartExecutorImpl` per spec ref.ap.8nwz2AHf503xwq8fKuLcl.E.

### New files
- `CompactionTrigger.kt` - enum with `DONE_BOUNDARY` variant (V1)
- `PrivateMdValidator.kt` - validates PRIVATE.md exists and is non-empty after compaction

### Modified files
- `PartExecutorImpl.kt` - core compaction logic: `performCompaction()`, done-boundary detection in `afterDone()`, session rotation support
- `PartExecutorImplTest.kt` - 10 new BDD tests covering all compaction scenarios
- `ShepherdValType.kt` - added `COMPACTION_TRIGGER` log value type

### Architecture changes in PartExecutorImpl
- `PartExecutorDeps` now includes `HarnessTimeoutConfig`, `SelfCompactionInstructionBuilder`, and `PrivateMdValidator`
- `afterDone()` returns `CompactionOutcome` (sealed class: NoCompaction/Compacted/CompactionFailed)
- Doer+reviewer path uses `DoerSignalResult`/`ReviewerSignalResult` sealed classes to communicate compaction state back to the loop
- `respawnAfterCompaction()` method for session rotation (skips NOT_STARTED state validation)
- Handle parameters changed to nullable where needed (killAllSessions, validatePublicMdOrCrash, terminateWith)

### Tests (all pass)
All 10 compaction scenarios tested via FakeAgentFacade. Full test suite green (42 tests).

## Decisions
- SelfCompacted in signal-mapping methods remains as `error()` -- compaction gets SelfCompacted directly from `sendPayloadAndAwaitSignal`, not through signal mapping
- Compaction threshold is inclusive (`remaining <= 35` triggers compaction)
- After compaction in doer-only path (COMPLETED), the part still returns Completed (compaction captures context for historical artifact)
