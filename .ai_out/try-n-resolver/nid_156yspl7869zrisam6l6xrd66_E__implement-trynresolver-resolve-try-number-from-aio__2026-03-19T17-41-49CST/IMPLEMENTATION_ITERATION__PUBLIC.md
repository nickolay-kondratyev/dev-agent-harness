# Implementation Iteration — TryNResolver

## Review Feedback Disposition

### 1. IMPORTANT: DRY violation — AI_OUT_DIR duplicated → **REJECTED**

**Reason**: `".ai_out"` is a path segment constant, not business logic. Extracting it to a shared location would create coupling between `core.filestructure` and `core.supporting.git` packages for a 6-character string that is spec-stable (ref.ap.BXQlLDTec7cVVOrzXWfR7.E). Both classes derive the constant independently from the spec, which is acceptable for path segments.

### 2. Suggestion: Test package mismatch → **REJECTED (already follows pattern)**

The test is at `com.glassthought.shepherd.core.git` which matches existing tests for `core.supporting.git` classes (e.g., `BranchNameBuilderTest`, `CommitAuthorBuilderTest` are all in `core.git` test package). The `supporting` layer is an implementation detail not mirrored in test packages.

### 3. Suggestion: Use BranchNameBuilder.build() in tests → **ACCEPTED**

Updated tests to use `BranchNameBuilder.build(ticketData, tryNumber)` instead of hardcoded branch name strings. Added a `createAiOutDir` helper to eliminate repetition. This keeps tests in sync if branch name format changes.

## Convergence
- All essential feedback addressed or rejected with rationale
- No blocking issues
- All tests pass
- Meets original requirements
