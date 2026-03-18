# Plan Review: BranchNameBuilder Hash-Based Truncation

## Executive Summary

The plan is well-structured, correctly implements the spec algorithm, and has comprehensive test coverage. There is one minor bug in the pseudocode (a scoping issue with `fullSlug` in `buildWordPrefix`) and one minor gap in the edge case table. Neither is blocking -- the implementer can resolve both trivially. **Recommendation: APPROVED WITH MINOR REVISIONS (inline-fixable, no iteration needed).**

## Critical Issues (BLOCKERS)

None.

## Major Concerns

None.

## Minor Issues

### 1. Pseudocode Bug: `fullSlug` out of scope in `buildWordPrefix`

**Location**: Phase 2 pseudocode, `buildWordPrefix` function (lines 93-106 of the plan).

**Issue**: The function references `fullSlug.take(budget).trimEnd('-')` on line 96, but `fullSlug` is not a parameter of `buildWordPrefix` -- it only receives `words: List<String>` and `budget: Int`. The `fullSlug` is a local variable in `truncateWithHash`.

**Fix**: Either pass `fullSlug` as a parameter to `buildWordPrefix`, or reconstruct it from `words.joinToString("-")` inside the function. The implementer should use `words.joinToString("-").take(budget).trimEnd('-')` for the single-long-word fallback, which is equivalent since `words` was derived from `fullSlug.split("-")`.

**Impact**: Low -- the intent is clear and any implementer will resolve this naturally.

### 2. Spec Example Hash Mismatch with Doc

**Observation**: The spec doc (`doc/core/git.md` line 63) uses `a1b2c3...` as a placeholder hash. The plan correctly computes the actual SHA-1: `c33b35`. The plan's test assertion for the canonical example uses the real hash value `c33b35` -- this is correct. Just noting the discrepancy for transparency: the spec doc's hash is illustrative, not authoritative.

### 3. Edge Case Table Missing: First Word Exactly Equals Budget

**Issue**: The edge case table (Phase 2) covers "First word > 43 chars" but not "First word == 43 chars exactly". This case is handled correctly by the algorithm (the word fits, no other words added, hash appended = 50 chars), but it would be worth adding as a test case to document the boundary.

**Suggestion**: Add a test case where the first word is exactly 43 chars (e.g., `"a".repeat(43) + "-more-words"`) and assert the result is exactly 50 chars: `"a".repeat(43) + "-" + hash6`.

## Simplification Opportunities (PARETO)

The plan is already PARETO-aligned. This is a single-method algorithm change with no architectural overhead. No simplification needed.

One optional simplification: instead of two separate private methods (`truncateWithHash` + `buildWordPrefix`), the implementer could keep it as a single `truncateWithHash` method since `buildWordPrefix` is only called from one place. However, the two-method decomposition is also fine for readability -- this is implementer's call.

## Minor Suggestions

1. **Test 3b.2 ("Exactly 50 chars -- no truncation")**: The plan says "Construct a title that slugifies to exactly 50 chars" but does not provide the actual test input. The implementer will need to craft this. Suggestion: use `"a".repeat(50)` which slugifies to exactly 50 `a` characters.

2. **Test 3b.3 ("51 chars -- triggers truncation")**: Similarly, `"a".repeat(51)` is the simplest input. The result would be: `"a".repeat(43) + "-" + sha1Hash6("a".repeat(51))` = 50 chars.

3. **Consider making `sha1Hash6` internal** (not private) for direct unit testing of the hash computation in isolation. This removes transitive testing dependency on `slugify` for hash correctness. However, this is optional -- transitive testing via `slugify` is also acceptable given the simplicity.

4. **Plan step 3c (update `build()` test)**: The suggestion to verify the slug "matches `Regex("-[a-f0-9]{6}$")`" is good but slightly fragile -- it assumes the slug always ends with a hash. For the specific test case at line 156-183 (which uses a long title that DOES trigger truncation), this is correct. Just ensure the implementer does not accidentally apply this assertion to short-slug tests.

## Strengths

- **Correct algorithm**: The word-boundary accumulation logic faithfully implements the spec. The SHA-1 computation, byte-to-hex conversion, and 6-char truncation are all correct. Verified by independent computation: `SHA-1("implement-user-authentication-flow-with-oauth-and-session")` starts with `c33b35`.

- **TDD approach**: Tests-first implementation order (Phase 3 listed but implementation order summary says "write tests first") aligns with project standards.

- **Comprehensive test matrix**: Covers no-truncation, word-boundary truncation, character-level fallback, determinism, uniqueness, and invariants. Maps directly to the 7 acceptance criteria in the ticket.

- **No over-engineering**: Single-method change, no new classes, no new dependencies, no architectural changes. Pure KISS.

- **Thread safety analysis**: Correctly identifies that `MessageDigest.getInstance()` returns new instances and `BranchNameBuilder` is stateless.

- **Spec gap note removal** (`doc/core/git.md` line 42): Good housekeeping.

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**Minor revisions are inline-fixable by the implementer. PLAN_ITERATION can be skipped -- proceed directly to implementation.**

### Summary of Inline Fixes for Implementer

1. Fix `buildWordPrefix` pseudocode scoping: pass `fullSlug` or reconstruct from `words.joinToString("-")`.
2. Add boundary test: first word exactly 43 chars + more words after.
3. Use concrete test inputs for "exactly 50" and "exactly 51" test cases (e.g., `"a".repeat(50)`, `"a".repeat(51)`).
