# Logical Review: BranchNameBuilder Hash-Based Truncation

## Verdict: READY

---

## Summary of Changes Reviewed

Replaced character-level `.take(50)` truncation in `BranchNameBuilder.slugify()` with a spec-compliant word-boundary preservation + SHA-1 hash suffix algorithm. The change touches:

- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt` — core implementation
- `app/src/test/kotlin/com/glassthought/shepherd/core/git/BranchNameBuilderTest.kt` — comprehensive new tests
- `doc/core/git.md` — updated spec example hash (`a1b2c3` -> `c33b35`), removed implementation gap note
- `_tickets/...` — ticket closed
- `_change_log/...` — change log entry added

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

None.

---

## Findings Verified Correct

### Invariant: result never exceeds MAX_SLUG_LENGTH (50 chars)

The math is airtight:
- `MAX_WORD_BUDGET = MAX_SLUG_LENGTH - HASH_SUFFIX_LENGTH = 50 - 7 = 43`
- `buildWordPrefix` returns at most 43 chars (both the word-boundary path and the character-level fallback path are bounded by `MAX_WORD_BUDGET`)
- `truncateWithHash` appends `-` + 6 hex chars = 7 chars
- Total maximum = 43 + 7 = 50 = `MAX_SLUG_LENGTH`. Invariant holds.

### Invariant: result never ends with a hyphen

`hash6` is always 6 hex characters `[a-f0-9]` — no hyphen possible. The `trimEnd('-')` in the character-level fallback path of `buildWordPrefix` ensures no trailing hyphen before the hash either.

### SHA-1 byte formatting correctness

The implementation uses `"%02x".format(it)` on `Byte` values from the SHA-1 `ByteArray`. Verified that the JVM correctly treats `byte` as unsigned when used with `%02x` in `String.format` — negative byte values (e.g., `-1` / `0xFF`) format as `ff`, not `ffffffff`. This is confirmed by: (a) all test expected hash values matching Python's `hashlib.sha1` output exactly, and (b) direct JVM verification.

### Spec compliance

All seven acceptance criteria from the ticket are met:
1. Word-boundary preservation on truncation — verified
2. First K whole words fitting in 43 chars + `-{hash6}` — verified
3. `hash6` = first 6 hex chars of SHA-1 of the **full** (untruncated) slug — verified
4. Result always <= 50 chars — verified by invariant analysis
5. Result never ends with hyphen — verified
6. Deterministic — SHA-1 is deterministic, `String.toByteArray(Charsets.UTF_8)` is deterministic
7. Test coverage — boundary cases (50/51 chars), single long word fallback, spec example, determinism, uniqueness all covered

### First-word boundary edge case (exactly 43 chars)

When the first word is exactly `MAX_WORD_BUDGET` (43) chars: the condition `words.first().length > MAX_WORD_BUDGET` is `43 > 43 = false`, so it correctly falls through to the accumulation loop. The first word (43 chars) is taken as the result, and the loop immediately breaks on the next candidate since `43 + 1 + len(nextWord) > 43`. Correct.

### Input sanitization precondition satisfied

`buildWordPrefix` receives `fullSlug` which has already been processed by `slugify`'s normalization pipeline (lowercase, non-alphanum replaced, consecutive hyphens collapsed, leading/trailing hyphens trimmed). This means:
- No leading/trailing hyphens in `fullSlug`
- No consecutive hyphens (`split("-")` produces no empty strings in the middle)
- `words.first()` is always non-empty

### Previously removed tests

The two tests removed from the original test suite (`WHEN called with a title exceeding 50 characters` and `WHEN called with a title that truncates to end with a hyphen`) are superseded by more precise tests covering the same behaviors plus edge cases. The behavioral assertions they captured (length <= 50, no trailing hyphen) are present in multiple new tests.

### Doc comment: stale "internal" visibility note

`slugify()` KDoc contains `"Visible as internal for direct testing of edge cases."` but the method is `fun` (public). This is a pre-existing stale comment carried over from the original. The doc comment numbering for the algorithm (step 7 "Fall back to untitled" listed last, while code checks empty first) is also slightly confusing. Neither is a logical issue.

---

## Tests

All tests pass. `./test.sh` exits 0. The `BranchNameBuilderTest` suite includes boundary cases at exactly 50 chars (no truncation), 51 chars (single-word char-level fallback), multi-word word-boundary truncation, the spec canonical example with verified hash value, determinism, and uniqueness for similar-prefix titles.
