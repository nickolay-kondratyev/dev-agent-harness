# Implementation: BranchNameBuilder Hash-Based Truncation

## Summary

Implemented the spec-compliant hash-based truncation algorithm in `BranchNameBuilder.slugify()`,
replacing the previous character-level `.take(50)` truncation with word-boundary preservation
and a SHA-1 hash suffix for uniqueness.

## What Changed

### `BranchNameBuilder.kt`

**New constants**:
- `HASH_HEX_CHAR_COUNT = 6` -- number of hex characters in hash suffix
- `HASH_BYTE_COUNT = 3` -- SHA-1 bytes needed for 6 hex chars
- `HASH_SUFFIX_LENGTH = 7` -- 1 hyphen + 6 hex chars
- `MAX_WORD_BUDGET = 43` -- MAX_SLUG_LENGTH - HASH_SUFFIX_LENGTH

**Refactored `slugify()`**: Computes the full slug first (without truncation), then uses a `when`
expression to decide: empty -> fallback, <= 50 -> as-is, > 50 -> `truncateWithHash()`.

**New private methods**:
- `truncateWithHash(fullSlug)` -- orchestrates word prefix + hash suffix
- `buildWordPrefix(fullSlug)` -- accumulates whole hyphen-delimited words within 43-char budget;
  falls back to character-level truncation if first word alone > 43 chars
- `sha1Hash6(input)` -- computes first 6 hex chars of SHA-1 using `java.security.MessageDigest`

### `BranchNameBuilderTest.kt`

Updated existing tests and added comprehensive new test cases:

| Test | What it verifies |
|------|-----------------|
| Exactly 50 chars | No truncation, no hash appended |
| Exactly 51 chars | Truncation triggers; 43 chars + hash = 50 total |
| 80-char single word | Character-level fallback with hash |
| First word exactly 43 chars + more words | First word kept, hash appended = 50 total |
| 49 'a' chars + "-bbb" (first word > 43) | Character-level fallback; never ends with hyphen |
| Spec canonical example | `"implement-user-authentication-flow-with-c33b35"` (46 chars) |
| Single 60-char word | Character-level fallback, exactly 50 chars |
| Determinism | Same input twice produces identical output |
| Uniqueness | Two similar titles with shared prefix produce different hashes |
| Invariants | Result never ends with hyphen; always <= 50 chars |
| Build with long title | Slug portion contains hash suffix (`-[a-f0-9]{6}$`) |

### `doc/core/git.md`

Removed the "Implementation gap" note (line 42) since the algorithm is now implemented per spec.

## Plan Review Minor Fixes Applied

1. **`buildWordPrefix` scoping**: Passed `fullSlug` as parameter (not referencing outer scope).
2. **First word exactly 43 chars test**: Added (`"a".repeat(43) + "-more-words"`).
3. **Concrete boundary test inputs**: Used `"a".repeat(50)` and `"a".repeat(51)` for exact boundary tests.

## Detekt Issues Resolved

- `ReturnCount`: Refactored `slugify()` from 3 return statements to a single `when` expression.
- `MagicNumber`: Extracted `3` to `HASH_BYTE_COUNT` constant (derived from `HASH_HEX_CHAR_COUNT / 2`).
- `MaxLineLength`: Split long string literal in test file across two lines.

## Test Results

All tests pass: `./gradlew :app:test` -- BUILD SUCCESSFUL.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt` | Algorithm change + new helpers |
| `app/src/test/kotlin/com/glassthought/shepherd/core/git/BranchNameBuilderTest.kt` | Updated + 12 new test cases |
| `doc/core/git.md` | Removed implementation gap note |
