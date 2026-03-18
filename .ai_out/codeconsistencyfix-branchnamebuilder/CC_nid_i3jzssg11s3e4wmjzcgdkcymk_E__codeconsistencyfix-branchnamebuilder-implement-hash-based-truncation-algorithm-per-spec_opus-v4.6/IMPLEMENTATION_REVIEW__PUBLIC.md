# Implementation Review: BranchNameBuilder Hash-Based Truncation

## Summary

The implementation correctly replaces character-level `.take(50)` truncation in
`BranchNameBuilder.slugify()` with the spec-compliant word-boundary preservation algorithm
using a SHA-1 hash suffix. The code is clean, well-structured, follows SRP, and all tests
pass (both `./sanity_check.sh` and `./gradlew :app:test`).

**Overall assessment: APPROVE with one documentation fix required.**

The implementation matches the spec algorithm, all acceptance criteria are tested, hash values
have been independently verified via Python SHA-1, removed tests are properly superseded by
strictly stronger test cases, and there are no production callers affected.

---

## No CRITICAL Issues

No security, correctness, or data loss issues found.

---

## IMPORTANT Issues

### 1. Spec example hash in `doc/core/git.md` is now stale (documentation inconsistency)

**File**: `doc/core/git.md`, lines 59-62

The spec example still uses placeholder hash `a1b2c3`:
```
hash of full slug: `a1b2c3...`
result: `"implement-user-authentication-flow-with-a1b2c3"` (46 chars <= 50)
```

Now that the algorithm is implemented, the actual SHA-1 of
`"implement-user-authentication-flow-with-oauth-and-session"` produces `c33b35`, not `a1b2c3`.
The test at line 143 of `BranchNameBuilderTest.kt` correctly asserts the real hash:
```kotlin
BranchNameBuilder.slugify(title) shouldBe
    "implement-user-authentication-flow-with-c33b35"
```

**Why this matters**: The spec is the source of truth. Having the spec say `a1b2c3` while the
implementation produces `c33b35` creates confusion for anyone reading the spec and comparing
against actual output. Now that we have a real implementation, the example should use the real
hash value.

**Fix**: Update `doc/core/git.md` lines 61-62 to:
```
-> hash of full slug: `c33b35...`
-> result: `"implement-user-authentication-flow-with-c33b35"` (46 chars <= 50)
```

---

## Suggestions

### 1. Pre-existing: `slugify` visibility comment says "internal" but function is public

**File**: `BranchNameBuilder.kt`, line 57

```kotlin
 * Visible as `internal` for direct testing of edge cases.
```

The function is actually `public` (no modifier). This comment pre-dates this change, so it is
not a regression. Mentioning it for awareness -- a follow-up could either make it `internal`
or update the comment.

---

## Verification Performed

1. **`./sanity_check.sh`** -- passed (exit 0)
2. **`./gradlew :app:test`** -- passed (BUILD SUCCESSFUL)
3. **Independent SHA-1 verification** (Python `hashlib.sha1`) confirmed all test-asserted hash
   values are correct:
   - `"a" * 51` -> `aca32b`
   - `"a" * 80` -> `86f336`
   - `"a" * 43 + "-more-words"` -> `d56f93`
   - `"a" * 49 + "-bbb"` -> `104124`
   - spec example slug -> `c33b35`
   - titleA uniqueness -> `8461c2`
   - titleB uniqueness -> `5187b8`
4. **Removed tests analysis** -- both removed tests from main are properly superseded by
   strictly stronger test cases in the new code (same inputs, same + additional assertions).
5. **No production callers** -- only test code references `BranchNameBuilder`; behavior change
   is safe.
6. **Detekt baseline** -- no pre-existing entries for `BranchNameBuilder`; no new violations.

## Acceptance Criteria Checklist

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Word-boundary preservation when truncating | PASS | `buildWordPrefix()` accumulates whole words |
| 2 | When slug > 50: first K words in 43 chars + `-{hash6}` | PASS | `truncateWithHash()` + spec example test |
| 3 | hash6 = first 6 hex of SHA-1 of full slug | PASS | `sha1Hash6()` verified against Python |
| 4 | Result always <= 50 chars | PASS | Multiple length assertions in tests |
| 5 | Result never ends with hyphen | PASS | Hash suffix guarantees this; explicit tests |
| 6 | Deterministic (same title -> same branch) | PASS | Determinism test case |
| 7 | Tests cover all required scenarios | PASS | See test coverage below |

## Test Coverage Analysis

| Scenario | Test Present |
|----------|-------------|
| Short title (no truncation) | Yes (simple title, special chars, etc.) |
| Exactly 50 chars (boundary, no truncation) | Yes |
| Exactly 51 chars (boundary, triggers truncation) | Yes |
| Long multi-word (word-boundary + hash) | Yes (spec canonical example) |
| Truncation point mid-word | Yes (49 'a' + '-bbb') |
| Single long word (char-level fallback) | Yes (60 chars, 80 chars) |
| First word exactly 43 chars | Yes |
| Determinism | Yes |
| Uniqueness for similar long titles | Yes |
| Never ends with hyphen | Yes |
| build() with long title includes hash | Yes |
