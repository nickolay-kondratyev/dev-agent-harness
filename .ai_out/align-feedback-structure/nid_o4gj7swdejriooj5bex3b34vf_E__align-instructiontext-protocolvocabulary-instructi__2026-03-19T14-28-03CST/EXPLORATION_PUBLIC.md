# Exploration: Align Feedback Structure

## Files to Modify

### 1. ProtocolVocabulary.kt
- `FeedbackStatus.UNADDRESSED = "unaddressed"` → rename to `PENDING = "pending"`
- ADD `FeedbackStatus.SKIPPED = "skipped"` (new resolution marker)
- KEEP `ADDRESSED`, `REJECTED`
- ADD `SeverityPrefix` object: `CRITICAL = "critical__"`, `IMPORTANT = "important__"`, `OPTIONAL = "optional__"`
- DELETE `MOVEMENT_LOG` constant (agents no longer write movement records)

### 2. InstructionText.kt
- `FEEDBACK_WRITING_INSTRUCTIONS`: Replace 9-dir structure with `__feedback/pending/` + severity filename prefixes. Remove Movement Log template, add `## Resolution:` marker instructions.
- `ADDRESSED_FEEDBACK_HEADER`: Minor wording OK (still uses FeedbackStatus.ADDRESSED)
- `REJECTED_FEEDBACK_HEADER`: References "move back to unaddressed/" → update to `pending/`
- `SKIPPED_OPTIONAL_HEADER`: References `unaddressed/optional/` path → update to `pending/optional__*` pattern

### 3. InstructionRenderers.kt
- `feedbackItemInstructions()`: Replace file-movement instructions with `## Resolution:` marker approach. Remove `addressedPath`/`rejectedPath` parameters. Remove Movement Log format section. Add SKIPPED option for optional items.

### 4. ContextForAgentProviderImpl.kt
- `feedbackStateSections()`: Change skipped-optional read from `unaddressed/optional/` to `pending/` filtering `optional__*` files
- `collectFeedbackFiles()`: Change from `{status}/{severity}/` traversal to flat directory reads
- Both `addressed/` and `rejected/` are now flat dirs with severity-prefixed filenames

### 5. Test Fixtures (`feedback/`)
- Old: `unaddressed/critical/missing-null-check.md`, `addressed/critical/fixed-race-condition.md`, `rejected/important/use-coroutine-scope.md`
- New: `pending/critical__missing-null-check.md`, `addressed/critical__fixed-race-condition.md`, `rejected/important__use-coroutine-scope.md`
- Replace `## Movement Log` sections with `## Resolution:` markers

### 6. ExecutionAgentInstructionsKeywordTest.kt
- Update `UNADDRESSED` assertion → `PENDING`
- Remove `MOVEMENT_LOG` assertion
- Fixture content assertions still work (titles unchanged)

### 7. ContextForAgentProviderAssemblyTest.kt
- Fixture path changes handled by restructured resource files
- Header assertions ("Addressed Feedback", "Rejected Feedback") unchanged

## Spec Reference
- `doc/plan/granular-feedback-loop.md` (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) — D1, D4, D6
