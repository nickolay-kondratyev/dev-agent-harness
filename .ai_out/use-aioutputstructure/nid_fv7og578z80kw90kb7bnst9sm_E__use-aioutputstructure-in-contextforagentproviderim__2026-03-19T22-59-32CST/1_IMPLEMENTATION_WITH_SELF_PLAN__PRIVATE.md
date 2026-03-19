# Implementation Private Notes

## Status: COMPLETE

All changes implemented and tests passing.

## Key decisions
- `resolvePrivateMdPath` returns `Path` (not `Path?`) since AiOutputStructure always returns a concrete path. The file may or may not exist on disk, which is handled by `PrivateMd.render()` via `Files.exists()` check.
- `SubPartConfig.privateMdPath` was intentionally kept — it's used by compaction logic in PartExecutorImpl, which is a separate concern from instruction assembly.
